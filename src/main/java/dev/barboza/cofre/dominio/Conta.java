package dev.barboza.cofre.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * O que toda conta do Cofre tem em comum (abstração e herança): número, titular, saldo, situação e o extrato.
 * Toda mudança de saldo passa por aqui e devolve o {@link Lancamento} correspondente, então saldo e extrato
 * nunca divergem (encapsulamento). Quanto pode sair da conta depende do tipo (polimorfismo): a
 * {@link ContaCorrente} soma o cheque especial; a {@link ContaPoupanca}, não.
 * <p>
 * As duas ficam na mesma tabela, diferenciadas pela coluna {@code tipo} (herança JPA em tabela única).
 */
@Entity
@Table(name = "conta")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo", length = 10)
public abstract class Conta {

    public static final String AGENCIA_PADRAO = "0001";

    private static final Pattern AGENCIA = Pattern.compile("\\d{4}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_base", nullable = false, unique = true)
    private int numeroBase;

    @Column(nullable = false, unique = true, length = 12)
    private String numero;

    @Column(nullable = false, length = 4)
    private String agencia;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SituacaoConta situacao;

    @Column(name = "motivo_bloqueio", length = 200)
    private String motivoBloqueio;

    @Column(name = "aberta_em", nullable = false)
    private Instant abertaEm;

    @Column(name = "encerrada_em")
    private Instant encerradaEm;

    /** Controle de concorrência otimista: duas operações simultâneas na mesma conta não se sobrescrevem. */
    @Version
    private long versao;

    protected Conta() {
    }

    protected Conta(int numeroBase, String agencia, Cliente cliente, Instant abertaEm) {
        this.numeroBase = numeroBase;
        this.numero = NumeroConta.formatar(numeroBase);
        this.agencia = validarAgencia(agencia);
        this.cliente = cliente;
        this.saldo = zero();
        this.situacao = SituacaoConta.ATIVA;
        this.abertaEm = abertaEm;
    }

    /** Lançamento de abertura com o depósito inicial (que pode ser zero). Usado pelas fábricas das subclasses. */
    protected static <T extends Conta> Abertura<T> abertura(T conta, BigDecimal depositoInicial, Instant quando) {
        BigDecimal valor = Dinheiro.validarValorOuZero(depositoInicial);
        Conta base = conta;
        base.saldo = base.saldo.add(valor);
        Lancamento lancamento = new Lancamento(conta, TipoLancamento.ABERTURA, valor, base.saldo, null, null, null,
                IdTransacao.gerar('T', quando), quando);
        return new Abertura<>(conta, lancamento);
    }

    public record Abertura<T extends Conta>(T conta, Lancamento lancamento) {
    }

    /** Corrente ou poupança. */
    public abstract TipoConta getTipo();

    /** Limite do cheque especial: o saldo pode ficar negativo até {@code -limite}. Zero na poupança. */
    public abstract BigDecimal getLimite();

    /** Outra ponta da operação (conta e nome), para o extrato e o comprovante. */
    public record Contraparte(String numero, String nome) {

        public static Contraparte de(Conta conta) {
            return new Contraparte(conta.getNumero(), conta.getCliente().getNome());
        }
    }

    /** Entrada de dinheiro. Conta bloqueada recebe normalmente; encerrada não. */
    public Lancamento creditar(TipoLancamento tipo, BigDecimal valor, Contraparte contraparte, String mensagem,
            String idTransacao, Instant quando) {
        if (!tipo.credito()) {
            throw new IllegalArgumentException(tipo + " não é crédito");
        }
        BigDecimal valido = Dinheiro.validarValor(valor);
        exigirNaoEncerrada();
        saldo = saldo.add(valido);
        return lancamento(tipo, valido, contraparte, mensagem, idTransacao, quando);
    }

    /**
     * Saída de dinheiro. Com {@code podeUsarLimite}, o saldo pode ficar negativo até o limite do cheque
     * especial; sem ele (ex.: guardar na caixinha), só o saldo próprio conta.
     */
    public Lancamento debitar(TipoLancamento tipo, BigDecimal valor, Contraparte contraparte, String mensagem,
            String idTransacao, Instant quando, boolean podeUsarLimite) {
        if (tipo.credito()) {
            throw new IllegalArgumentException(tipo + " não é débito");
        }
        BigDecimal valido = Dinheiro.validarValor(valor);
        exigirAtiva();
        BigDecimal disponivel = podeUsarLimite ? disponivel() : saldo.max(zero());
        if (disponivel.compareTo(valido) < 0) {
            throw new SaldoInsuficienteException(disponivel, valido, podeUsarLimite && getLimite().signum() > 0);
        }
        saldo = saldo.subtract(valido);
        return lancamento(tipo, valido, contraparte, mensagem, idTransacao, quando);
    }

    /** Juros do cheque especial: cobrados mesmo com a conta bloqueada e mesmo passando do limite. */
    public Lancamento cobrarJuros(BigDecimal valor, String idTransacao, String mensagem, Instant quando) {
        BigDecimal valido = Dinheiro.validarValor(valor);
        exigirNaoEncerrada();
        saldo = saldo.subtract(valido);
        return lancamento(TipoLancamento.JUROS_CHEQUE_ESPECIAL, valido, null, mensagem, idTransacao, quando);
    }

    public void bloquear(String motivo) {
        exigirNaoEncerrada();
        if (situacao == SituacaoConta.BLOQUEADA) {
            throw new OperacaoInvalidaException("A conta " + numero + " já está bloqueada.");
        }
        String texto = motivo == null ? "" : motivo.trim();
        if (texto.length() < 5) {
            throw new OperacaoInvalidaException("Informe o motivo do bloqueio (pelo menos 5 caracteres).");
        }
        situacao = SituacaoConta.BLOQUEADA;
        motivoBloqueio = texto.length() > 200 ? texto.substring(0, 200) : texto;
    }

    public void desbloquear() {
        if (situacao != SituacaoConta.BLOQUEADA) {
            throw new OperacaoInvalidaException("A conta " + numero + " não está bloqueada.");
        }
        situacao = SituacaoConta.ATIVA;
        motivoBloqueio = null;
    }

    /** Só encerra com saldo zero, para não "sumir" com dinheiro do cliente nem com dívida do banco. */
    public void encerrar(Instant quando) {
        exigirNaoEncerrada();
        if (saldo.signum() != 0) {
            throw new OperacaoInvalidaException("Para encerrar, o saldo precisa estar zerado. Saldo atual: "
                    + Dinheiro.formatar(saldo) + ".");
        }
        situacao = SituacaoConta.ENCERRADA;
        encerradaEm = quando;
        aoEncerrar();
    }

    /** Gancho para cada tipo de conta limpar o que for dela ao encerrar (a corrente zera o limite). */
    protected void aoEncerrar() {
    }

    /** Saldo + limite do cheque especial (que é zero na poupança). */
    public BigDecimal disponivel() {
        return saldo.add(getLimite());
    }

    /** Quanto do cheque especial está em uso (zero se o saldo for positivo). */
    public BigDecimal usoDoLimite() {
        return saldo.signum() < 0 ? saldo.negate() : zero();
    }

    public boolean pertenceA(Long clienteId) {
        return clienteId != null && clienteId.equals(cliente.getId());
    }

    /** Mensagem de boas-vindas do desafio original do terminal. */
    public String mensagemDeBoasVindas() {
        return "Olá " + cliente.getNome() + ", obrigado por criar uma conta em nosso banco, sua agência é " + agencia
                + ", conta " + numero + " e seu saldo " + Dinheiro.formatar(saldo) + " já está disponível para saque.";
    }

    protected Lancamento lancamento(TipoLancamento tipo, BigDecimal valor, Contraparte contraparte, String mensagem,
            String idTransacao, Instant quando) {
        return new Lancamento(this, tipo, valor, saldo, contraparte == null ? null : contraparte.numero(),
                contraparte == null ? null : contraparte.nome(), limparMensagem(mensagem), idTransacao, quando);
    }

    public void exigirAtiva() {
        exigirNaoEncerrada();
        if (situacao == SituacaoConta.BLOQUEADA) {
            throw new OperacaoInvalidaException("A conta " + numero + " está bloqueada para saídas. Procure seu gerente.");
        }
    }

    protected void exigirNaoEncerrada() {
        if (situacao == SituacaoConta.ENCERRADA) {
            throw new OperacaoInvalidaException("A conta " + numero + " está encerrada.");
        }
    }

    private static String limparMensagem(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return null;
        }
        String texto = mensagem.trim().replaceAll("\\s+", " ");
        if (texto.length() > 140) {
            throw new OperacaoInvalidaException("A mensagem pode ter no máximo 140 caracteres.");
        }
        return texto;
    }

    private static String validarAgencia(String agencia) {
        String valor = agencia == null || agencia.isBlank() ? AGENCIA_PADRAO : agencia.trim();
        if (!AGENCIA.matcher(valor).matches()) {
            throw new OperacaoInvalidaException("A agência deve ter 4 dígitos, por exemplo 0001.");
        }
        return valor;
    }

    protected static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    public Long getId() {
        return id;
    }

    public int getNumeroBase() {
        return numeroBase;
    }

    public String getNumero() {
        return numero;
    }

    public String getAgencia() {
        return agencia;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public SituacaoConta getSituacao() {
        return situacao;
    }

    public String getMotivoBloqueio() {
        return motivoBloqueio;
    }

    public Instant getAbertaEm() {
        return abertaEm;
    }

    public Instant getEncerradaEm() {
        return encerradaEm;
    }
}
