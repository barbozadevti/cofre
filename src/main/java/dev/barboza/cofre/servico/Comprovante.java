package dev.barboza.cofre.servico;

import java.math.BigDecimal;
import java.time.Instant;

/** Comprovante de uma transação, com os dados de quem pagou e de quem recebeu (CPF mascarado). */
public record Comprovante(
        String idTransacao,
        String tipo,
        String titulo,
        Instant dataHora,
        BigDecimal valor,
        Parte origem,
        Parte destino,
        String mensagem) {

    public record Parte(String nome, String cpfMascarado, String agencia, String conta) {
    }
}
