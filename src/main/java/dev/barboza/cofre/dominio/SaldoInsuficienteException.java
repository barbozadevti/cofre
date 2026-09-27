package dev.barboza.cofre.dominio;

import java.math.BigDecimal;

public class SaldoInsuficienteException extends OperacaoInvalidaException {

    public SaldoInsuficienteException(BigDecimal disponivel, BigDecimal solicitado) {
        super("Saldo insuficiente: disponível " + Dinheiro.formatar(disponivel)
                + ", solicitado " + Dinheiro.formatar(solicitado) + ".");
    }
}
