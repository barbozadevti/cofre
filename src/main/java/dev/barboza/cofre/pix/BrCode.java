package dev.barboza.cofre.pix;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.barboza.cofre.dominio.OperacaoInvalidaException;

/**
 * "Pix copia e cola" (BR Code) no padrão EMV QRCPS do Banco Central: campos TLV
 * (id de 2 dígitos + tamanho de 2 dígitos + valor), terminando com CRC16-CCITT (campo 63).
 */
public final class BrCode {

    private static final String GUI_PIX = "br.gov.bcb.pix";

    private BrCode() {
    }

    public record Dados(String chave, String nome, String cidade, BigDecimal valor, String txid, String descricao) {
    }

    public static String gerar(Dados dados) {
        StringBuilder conta = new StringBuilder()
                .append(campo("00", GUI_PIX))
                .append(campo("01", dados.chave()));
        if (dados.descricao() != null && !dados.descricao().isBlank()) {
            conta.append(campo("02", ascii(dados.descricao(), 40)));
        }
        StringBuilder payload = new StringBuilder()
                .append(campo("00", "01"))
                .append(campo("26", conta.toString()))
                .append(campo("52", "0000"))
                .append(campo("53", "986"));
        if (dados.valor() != null) {
            payload.append(campo("54", dados.valor().setScale(2, RoundingMode.UNNECESSARY).toPlainString()));
        }
        payload.append(campo("58", "BR"))
                .append(campo("59", ascii(dados.nome(), 25)))
                .append(campo("60", ascii(dados.cidade(), 15)))
                .append(campo("62", campo("05", dados.txid() == null || dados.txid().isBlank() ? "***" : dados.txid())))
                .append("6304");
        return payload + crc16(payload.toString());
    }

    /** Lê e confere um código copia e cola: estrutura, moeda, identificador do Pix e CRC. */
    public static Dados ler(String codigo) {
        String payload = codigo == null ? "" : codigo.trim();
        if (payload.length() < 30 || !payload.substring(payload.length() - 8, payload.length() - 4).equals("6304")) {
            throw invalido("o código está incompleto");
        }
        String semCrc = payload.substring(0, payload.length() - 4);
        if (!crc16(semCrc).equalsIgnoreCase(payload.substring(payload.length() - 4))) {
            throw invalido("o dígito de conferência (CRC) não bate; confira se o código foi copiado inteiro");
        }
        Map<String, String> campos = campos(semCrc.substring(0, semCrc.length() - 4));
        if (!"01".equals(campos.get("00"))) {
            throw invalido("formato não reconhecido");
        }
        Map<String, String> conta = campos(campos.getOrDefault("26", ""));
        if (!GUI_PIX.equalsIgnoreCase(conta.get("00")) || conta.get("01") == null) {
            throw invalido("não é um código Pix com chave");
        }
        if (!"986".equals(campos.get("53"))) {
            throw invalido("a moeda não é o real");
        }
        BigDecimal valor = null;
        if (campos.containsKey("54")) {
            try {
                valor = new BigDecimal(campos.get("54"));
            } catch (NumberFormatException e) {
                throw invalido("valor inválido");
            }
        }
        String txid = campos.containsKey("62") ? campos(campos.get("62")).get("05") : null;
        return new Dados(conta.get("01"), campos.get("59"), campos.get("60"), valor,
                "***".equals(txid) ? null : txid, conta.get("02"));
    }

    /** CRC16-CCITT-FALSE (polinômio 0x1021, início 0xFFFF), em 4 dígitos hexadecimais maiúsculos. */
    static String crc16(String texto) {
        int crc = 0xFFFF;
        for (byte b : texto.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }

    private static String campo(String id, String valor) {
        if (valor.length() > 99) {
            throw new OperacaoInvalidaException("Campo " + id + " do código Pix passou de 99 caracteres.");
        }
        return id + String.format("%02d", valor.length()) + valor;
    }

    private static Map<String, String> campos(String texto) {
        Map<String, String> campos = new LinkedHashMap<>();
        int i = 0;
        while (i < texto.length()) {
            if (i + 4 > texto.length()) {
                throw invalido("estrutura corrompida");
            }
            String id = texto.substring(i, i + 2);
            int tamanho;
            try {
                tamanho = Integer.parseInt(texto.substring(i + 2, i + 4));
            } catch (NumberFormatException e) {
                throw invalido("estrutura corrompida");
            }
            if (i + 4 + tamanho > texto.length()) {
                throw invalido("estrutura corrompida");
            }
            campos.put(id, texto.substring(i + 4, i + 4 + tamanho));
            i += 4 + tamanho;
        }
        return campos;
    }

    /** O BR Code usa só ASCII: remove acentos e corta no tamanho máximo do campo. */
    static String ascii(String texto, int maximo) {
        String semAcento = Normalizer.normalize(texto == null ? "" : texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^\\x20-\\x7E]", "");
        return semAcento.length() > maximo ? semAcento.substring(0, maximo) : semAcento;
    }

    private static OperacaoInvalidaException invalido(String motivo) {
        return new OperacaoInvalidaException("Código Pix inválido: " + motivo + ".");
    }
}
