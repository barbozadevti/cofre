package dev.barboza.cofre.cartao;

/** Dígito verificador de cartões (algoritmo de Luhn, ISO/IEC 7812). */
public final class Luhn {

    private Luhn() {
    }

    public static int digito(String semDigito) {
        int soma = 0;
        boolean dobrar = true;
        for (int i = semDigito.length() - 1; i >= 0; i--) {
            int d = semDigito.charAt(i) - '0';
            if (dobrar) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            soma += d;
            dobrar = !dobrar;
        }
        return (10 - soma % 10) % 10;
    }

    public static boolean valido(String numero) {
        return numero != null && numero.matches("\\d{12,19}")
                && digito(numero.substring(0, numero.length() - 1)) == numero.charAt(numero.length() - 1) - '0';
    }
}
