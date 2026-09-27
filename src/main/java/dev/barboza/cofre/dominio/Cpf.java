package dev.barboza.cofre.dominio;

/** Validação, formatação e mascaramento de CPF (LGPD: só o miolo aparece para terceiros). */
public final class Cpf {

    private Cpf() {
    }

    /** Devolve só os 11 dígitos, conferindo os dígitos verificadores. */
    public static String validar(String cpf) {
        String digitos = cpf == null ? "" : cpf.replaceAll("\\D", "");
        if (digitos.length() != 11 || digitos.chars().distinct().count() == 1) {
            throw new OperacaoInvalidaException("CPF inválido.");
        }
        if (digito(digitos, 9) != digitos.charAt(9) - '0' || digito(digitos, 10) != digitos.charAt(10) - '0') {
            throw new OperacaoInvalidaException("CPF inválido: os dígitos verificadores não conferem.");
        }
        return digitos;
    }

    public static boolean valido(String cpf) {
        try {
            validar(cpf);
            return true;
        } catch (OperacaoInvalidaException e) {
            return false;
        }
    }

    /** Calcula o dígito na posição {@code posicao} (9 ou 10) a partir dos anteriores. */
    static int digito(String digitos, int posicao) {
        int soma = 0;
        for (int i = 0; i < posicao; i++) {
            soma += (digitos.charAt(i) - '0') * (posicao + 1 - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /** Completa 9 dígitos com os 2 verificadores (usado para gerar CPFs de demonstração). */
    public static String completar(String noveDigitos) {
        String com10 = noveDigitos + digito(noveDigitos, 9);
        return com10 + digito(com10, 10);
    }

    public static String formatar(String cpf) {
        String d = validar(cpf);
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    /** {@code ***.456.789-**}: formato usado pelos bancos para mostrar o CPF de terceiros no Pix. */
    public static String mascarar(String cpf) {
        String d = validar(cpf);
        return "***." + d.substring(3, 6) + "." + d.substring(6, 9) + "-**";
    }
}
