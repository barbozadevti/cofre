package dev.barboza.cofre.dominio;

/** Celular brasileiro no formato E.164 usado pelas chaves Pix: {@code +5511987654321}. */
public final class Telefone {

    private Telefone() {
    }

    public static String normalizar(String telefone) {
        String digitos = telefone == null ? "" : telefone.replaceAll("\\D", "");
        if (digitos.startsWith("55") && digitos.length() == 13) {
            digitos = digitos.substring(2);
        }
        if (digitos.length() != 11 || digitos.charAt(2) != '9' || digitos.charAt(0) == '0') {
            throw new OperacaoInvalidaException("Celular inválido: use DDD + 9 dígitos, por exemplo (11) 98765-4321.");
        }
        return "+55" + digitos;
    }
}
