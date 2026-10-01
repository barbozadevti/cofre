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

    @Override
    protected void aoEncerrar() {
        limite = zero();
    }
}
