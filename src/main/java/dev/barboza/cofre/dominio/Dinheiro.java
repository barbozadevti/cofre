package dev.barboza.cofre.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Regras e formatação de valores monetários (sempre {@link BigDecimal} com 2 casas). */
public final class Dinheiro {

    public static final BigDecimal LIMITE_POR_OPERACAO = new BigDecimal("1000000.00");

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private Dinheiro() {
    }

    /** Confere e normaliza o valor de uma operação: positivo, até 2 casas e dentro do limite. */
    public static BigDecimal validarValor(BigDecimal valor) {
        if (valor == null) {
            throw new OperacaoInvalidaException("Informe o valor.");
        }
        if (valor.signum() <= 0) {
            throw new OperacaoInvalidaException("O valor deve ser maior que zero.");
        }
        return validarFormato(valor);
    }

    /** Como {@link #validarValor}, mas aceita zero (ex.: conta aberta sem depósito inicial). */
    public static BigDecimal validarValorOuZero(BigDecimal valor) {
        if (valor == null) {
            throw new OperacaoInvalidaException("Informe o valor.");
        }
        if (valor.signum() < 0) {
            throw new OperacaoInvalidaException("O valor não pode ser negativo.");
        }
        return validarFormato(valor);
    }

    private static BigDecimal validarFormato(BigDecimal valor) {
        if (valor.stripTrailingZeros().scale() > 2) {
            throw new OperacaoInvalidaException("O valor deve ter no máximo 2 casas decimais.");
        }
        if (valor.compareTo(LIMITE_POR_OPERACAO) > 0) {
            throw new OperacaoInvalidaException("O valor máximo por operação é " + formatar(LIMITE_POR_OPERACAO) + ".");
        }
        return valor.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Formata no padrão brasileiro: {@code R$ 1.234,56} (negativos como {@code -R$ 10,00}). */
    public static String formatar(BigDecimal valor) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        String numero = formato.format(valor.abs());
        return (valor.signum() < 0 ? "-R$ " : "R$ ") + numero;
    }

    /** Lê um valor digitado por uma pessoa: aceita {@code 1.234,56}, {@code 1234,56} e {@code 1234.56}. */
    public static BigDecimal interpretar(String texto) {
        String limpo = texto == null ? "" : texto.trim().replace("R$", "").replace(" ", "");
        if (limpo.contains(",")) {
            limpo = limpo.replace(".", "").replace(',', '.');
        }
        try {
            return new BigDecimal(limpo);
        } catch (NumberFormatException e) {
            throw new OperacaoInvalidaException("Valor inválido: use números, por exemplo 150,75.");
        }
    }
}
