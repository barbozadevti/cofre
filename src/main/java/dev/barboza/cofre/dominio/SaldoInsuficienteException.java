package dev.barboza.cofre.dominio;

import java.math.BigDecimal;

public class SaldoInsuficienteException extends OperacaoInvalidaException {

    public SaldoInsuficienteException(BigDecimal disponivel, BigDecimal solicitado, boolean incluiLimite) {
        super("Saldo insuficiente: disponível " + Dinheiro.formatar(disponivel)
                + (incluiLimite ? " (com o cheque especial)" : "")
                + ", solicitado " + Dinheiro.formatar(solicitado) + ".");
    }
}
