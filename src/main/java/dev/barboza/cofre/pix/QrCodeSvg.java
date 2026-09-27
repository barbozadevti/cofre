package dev.barboza.cofre.pix;

import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/** Gera o QR Code como SVG (vetorial, nítido em qualquer tamanho e sem imagem binária). */
public final class QrCodeSvg {

    private static final int MARGEM = 4;

    private QrCodeSvg() {
    }

    public static String gerar(String texto) {
        BitMatrix matriz;
        try {
            matriz = new QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0,
                            EncodeHintType.CHARACTER_SET, "UTF-8"));
        } catch (WriterException e) {
            throw new IllegalStateException("Não foi possível gerar o QR Code", e);
        }
        int lado = matriz.getWidth() + 2 * MARGEM;
        StringBuilder caminho = new StringBuilder();
        for (int y = 0; y < matriz.getHeight(); y++) {
            for (int x = 0; x < matriz.getWidth(); x++) {
                if (matriz.get(x, y)) {
                    caminho.append('M').append(x + MARGEM).append(' ').append(y + MARGEM).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + lado + " " + lado
                + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR Code Pix\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
                + "<path fill=\"#000\" d=\"" + caminho + "\"/></svg>";
    }
}
