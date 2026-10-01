package dev.barboza.cofre.dominio;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** Conta corrente: a única que tem cheque especial (e, por isso, juros diários sobre o saldo negativo). */
@Entity
@DiscriminatorValue("CORRENTE")
public class ContaCorrente extends Conta {

    public static final BigDecimal LIMITE_MAXIMO = new BigDecimal("50000.00");

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal limite;

    /** Escudo de juros: cobrir o saldo negativo com a poupança do cliente antes de cobrar juros. */
    @Column(name = "escudo_ativo", nullable = false)
    private boolean escudoAtivo;

    @Column(name = "escudo_desde")
    private Instant escudoDesde;

    protected ContaCorrente() {
    }

    private ContaCorrente(int numeroBase, String agencia, Cliente cliente, Instant abertaEm) {
        super(numeroBase, agencia, cliente, abertaEm);
        this.limite = zero();
    }

    /** Cria a conta e devolve junto o lançamento de abertura com o depósito inicial (que pode ser zero). */
    public static Abertura<ContaCorrente> abrir(int numeroBase, String agencia, Cliente cliente,
            BigDecimal depositoInicial, Instant quando) {
        return abertura(new ContaCorrente(numeroBase, agencia, cliente, quando), depositoInicial, quando);
    }

    @Override
    public TipoConta getTipo() {
        return TipoConta.CORRENTE;
    }

    @Override
    public BigDecimal getLimite() {
        return limite;
    }

    /** Define o limite do cheque especial. Não deixa baixar abaixo do que o cliente já está usando. */
    public void definirLimite(BigDecimal novoLimite) {
        exigirNaoEncerrada();
        BigDecimal valor = Dinheiro.validarValorOuZero(novoLimite);
        if (valor.compareTo(LIMITE_MAXIMO) > 0) {
            throw new OperacaoInvalidaException("O limite máximo do cheque especial é " + Dinheiro.formatar(LIMITE_MAXIMO) + ".");
        }
        if (usoDoLimite().compareTo(valor) > 0) {
            throw new OperacaoInvalidaException("O cliente está usando " + Dinheiro.formatar(usoDoLimite())
                    + " do cheque especial; o novo limite não pode ser menor que isso.");
        }
        limite = valor;
    }

    public void ativarEscudo(Instant quando) {
        exigirAtiva();
        if (escudoAtivo) {
            throw new OperacaoInvalidaException("O Escudo de juros já está ativo.");
        }
        escudoAtivo = true;
        escudoDesde = quando;
    }

    public void desativarEscudo() {
        if (!escudoAtivo) {
            throw new OperacaoInvalidaException("O Escudo de juros já está desligado.");
        }
        escudoAtivo = false;
        escudoDesde = null;
    }

    /** Quanto o Escudo precisa trazer para a conta voltar a zero (zero se não estiver negativa). */
    public BigDecimal faltaParaZerar() {
        return usoDoLimite();
    }

    public boolean isEscudoAtivo() {
        return escudoAtivo;
    }

    public Instant getEscudoDesde() {
        return escudoDesde;
    }

    @Override
    protected void aoEncerrar() {
        limite = zero();
        escudoAtivo = false;
        escudoDesde = null;
    }
}
