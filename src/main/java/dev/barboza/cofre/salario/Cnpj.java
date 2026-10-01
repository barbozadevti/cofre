package dev.barboza.cofre.salario;

import dev.barboza.cofre.dominio.OperacaoInvalidaException;

/** CNPJ numérico (14 dígitos) validado pelos dois dígitos verificadores. */
public final class Cnpj {

    private static final int[] PESOS_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private Cnpj() {
    }

    public static String validar(String cnpj) {
        String d = cnpj == null ? "" : cnpj.replaceAll("[^0-9]", "");
        if (d.length() != 14 || d.chars().distinct().count() == 1
                || digito(d, PESOS_1) != d.charAt(12) - '0' || digito(d, PESOS_2) != d.charAt(13) - '0') {
            throw new OperacaoInvalidaException("CNPJ do empregador inválido.");
        }
        return d;
    }

    public static String formatar(String d) {
        return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "/" + d.substring(8, 12) + "-" + d.substring(12);
    }

    private static int digito(String d, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (d.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
