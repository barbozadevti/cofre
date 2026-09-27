package dev.barboza.cofre.dominio;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Identificador de transação no formato do "end-to-end ID" do Pix (32 caracteres):
 * letra do tipo + ISPB fictício do Cofre (8) + data e hora UTC (yyyyMMddHHmm) + 11 caracteres aleatórios.
 */
public final class IdTransacao {

    public static final String ISPB_COFRE = "31415926";

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
    private static final SecureRandom SORTEIO = new SecureRandom();

    private IdTransacao() {
    }

    /** {@code E...} para Pix (como no SPI do Banco Central) e {@code T...} para as demais operações. */
    public static String gerar(char prefixo, Instant quando) {
        StringBuilder id = new StringBuilder(32).append(prefixo).append(ISPB_COFRE).append(DATA.format(quando));
        for (int i = 0; i < 11; i++) {
            id.append(ALFABETO.charAt(SORTEIO.nextInt(ALFABETO.length())));
        }
        return id.toString();
    }
}
