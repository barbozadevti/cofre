package dev.barboza.cofre.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Conta corrente. Toda mudança de saldo passa por aqui e devolve o {@link Lancamento}
 * correspondente, então saldo e extrato nunca divergem.
 */
@Entity
@Table(name = "conta")
public class Conta {

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

    @Column(nullable = false, length = 80)
    private String titular;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SituacaoConta situacao;

    @Column(name = "aberta_em", nullable = false)
    private Instant abertaEm;

    @Column(name = "encerrada_em")
    private Instant encerradaEm;

    /** Controle de concorrência otimista: duas operações simultâneas na mesma conta não se sobrescrevem. */
    @Version
    private long versao;

    protected Conta() {
    }

    private Conta(int numeroBase, String agencia, String titular, Instant abertaEm) {
        this.numeroBase = numeroBase;
        this.numero = NumeroConta.formatar(numeroBase);
        this.agencia = agencia;
        this.titular = titular;
        this.saldo = BigDecimal.ZERO.setScale(2);
        this.situacao = SituacaoConta.ATIVA;
        this.abertaEm = abertaEm;
    }

    /** Cria a conta e devolve junto o lançamento de abertura com o saldo inicial (que pode ser zero). */
    public static Abertura abrir(int numeroBase, String agencia, String titular, BigDecimal saldoInicial, Instant quando) {
        Conta conta = new Conta(numeroBase, validarAgencia(agencia), validarTitular(titular), quando);
        BigDecimal valor = Dinheiro.validarValorOuZero(saldoInicial);
        Lancamento abertura = conta.creditar(TipoLancamento.ABERTURA, valor, null, quando);
        return new Abertura(conta, abertura);
    }

    public record Abertura(Conta conta, Lancamento lancamento) {
    }

    public Lancamento depositar(BigDecimal valor, Instant quando) {
        return creditar(TipoLancamento.DEPOSITO, Dinheiro.validarValor(valor), null, quando);
    }

    public Lancamento sacar(BigDecimal valor, Instant quando) {
        return debitar(TipoLancamento.SAQUE, Dinheiro.validarValor(valor), null, quando);
    }

    /** Debita desta conta e credita no destino; devolve os dois lançamentos (enviado, recebido). */
    public Lancamento[] transferir(Conta destino, BigDecimal valor, Instant quando) {
        if (destino.numero.equals(numero)) {
            throw new OperacaoInvalidaException("A conta de destino deve ser diferente da conta de origem.");
        }
        BigDecimal valido = Dinheiro.validarValor(valor);
        destino.exigirAtiva();
        Lancamento enviado = debitar(TipoLancamento.TRANSFERENCIA_ENVIADA, valido, destino.numero, quando);
        Lancamento recebido = destino.creditar(TipoLancamento.TRANSFERENCIA_RECEBIDA, valido, numero, quando);
        return new Lancamento[] {enviado, recebido};
    }

    /** Só encerra com saldo zero, para não "sumir" com dinheiro do cliente. */
    public void encerrar(Instant quando) {
        exigirAtiva();
        if (saldo.signum() != 0) {
            throw new OperacaoInvalidaException("Para encerrar, o saldo precisa estar zerado. Saldo atual: "
                    + Dinheiro.formatar(saldo) + ".");
        }
        situacao = SituacaoConta.ENCERRADA;
        encerradaEm = quando;
    }

    /** Mensagem de boas-vindas do desafio original do terminal. */
    public String mensagemDeBoasVindas() {
        return "Olá " + titular + ", obrigado por criar uma conta em nosso banco, sua agência é " + agencia
                + ", conta " + numero + " e seu saldo " + Dinheiro.formatar(saldo) + " já está disponível para saque.";
    }

    private Lancamento creditar(TipoLancamento tipo, BigDecimal valor, String contraparte, Instant quando) {
        exigirAtiva();
        saldo = saldo.add(valor);
        return new Lancamento(this, tipo, valor, saldo, contraparte, quando);
    }

    private Lancamento debitar(TipoLancamento tipo, BigDecimal valor, String contraparte, Instant quando) {
        exigirAtiva();
        if (saldo.compareTo(valor) < 0) {
            throw new SaldoInsuficienteException(saldo, valor);
        }
        saldo = saldo.subtract(valor);
        return new Lancamento(this, tipo, valor, saldo, contraparte, quando);
    }

    private void exigirAtiva() {
        if (situacao == SituacaoConta.ENCERRADA) {
            throw new OperacaoInvalidaException("A conta " + numero + " está encerrada.");
        }
    }

    private static String validarAgencia(String agencia) {
        String valor = agencia == null || agencia.isBlank() ? AGENCIA_PADRAO : agencia.trim();
        if (!AGENCIA.matcher(valor).matches()) {
            throw new OperacaoInvalidaException("A agência deve ter 4 dígitos, por exemplo 0001.");
        }
        return valor;
    }

    private static String validarTitular(String titular) {
        String nome = titular == null ? "" : titular.trim().replaceAll("\\s+", " ");
        if (nome.length() < 3) {
            throw new OperacaoInvalidaException("Informe o nome do titular (pelo menos 3 letras).");
        }
        if (nome.length() > 80) {
            throw new OperacaoInvalidaException("O nome do titular pode ter no máximo 80 caracteres.");
        }
        if (!nome.matches("[\\p{L} .'-]+")) {
            throw new OperacaoInvalidaException("O nome do titular deve ter apenas letras.");
        }
        return nome;
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

    public String getTitular() {
        return titular;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public SituacaoConta getSituacao() {
        return situacao;
    }

    public Instant getAbertaEm() {
        return abertaEm;
    }

    public Instant getEncerradaEm() {
        return encerradaEm;
    }
}
