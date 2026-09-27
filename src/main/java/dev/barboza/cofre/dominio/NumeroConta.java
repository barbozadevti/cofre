package dev.barboza.cofre.dominio;

/**
 * Número de conta no formato {@code NNNNN-D}, com dígito verificador pelo módulo 11
 * (pesos 2 a 9 da direita para a esquerda; restos 0 e 1 dão dígito 0).
 */
public final class NumeroConta {

    private NumeroConta() {
    }

    public static String formatar(int base) {
        return String.format("%05d-%d", base, digito(base));
    }

    static int digito(int base) {
        String digitos = String.format("%05d", base);
        int soma = 0;
        int peso = 2;
        for (int i = digitos.length() - 1; i >= 0; i--) {
            soma += Character.getNumericValue(digitos.charAt(i)) * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /**
     * Aceita o número com ou sem hífen ({@code 10001-7} ou {@code 100017}) e devolve o formato
     * canônico, conferindo o dígito verificador.
     */
    public static String normalizar(String numero) {
        String digitado = numero == null ? "" : numero.trim();
        String digitos = digitado.replaceAll("\\D", "");
        if (digitos.length() < 2 || digitos.length() > 9) {
            throw new OperacaoInvalidaException("Número de conta inválido: use o formato 10001-7.");
        }
        int base = Integer.parseInt(digitos.substring(0, digitos.length() - 1));
        int dv = Character.getNumericValue(digitos.charAt(digitos.length() - 1));
        if (digito(base) != dv) {
            throw new OperacaoInvalidaException("Número de conta inválido: o dígito verificador de "
                    + digitado + " não confere.");
        }
        return formatar(base);
    }
}
