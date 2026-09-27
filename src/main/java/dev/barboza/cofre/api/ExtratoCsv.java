package dev.barboza.cofre.api;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

import dev.barboza.cofre.dominio.Lancamento;
import dev.barboza.cofre.servico.ContaService;
import dev.barboza.cofre.servico.Extrato;

final class ExtratoCsv {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ContaService.FUSO);

    private ExtratoCsv() {
    }

    static String gerar(Extrato extrato) {
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Data;Descrição;Valor;Saldo\r\n");
        csv.append(";Saldo anterior;;").append(numero(extrato.saldoInicial())).append("\r\n");
        for (Lancamento l : extrato.lancamentos()) {
            csv.append(DATA_HORA.format(l.getDataHora())).append(';')
                    .append(texto(l.descricao())).append(';')
                    .append(numero(l.valorComSinal())).append(';')
                    .append(numero(l.getSaldoApos())).append("\r\n");
        }
        return csv.toString();
    }

    private static String numero(BigDecimal valor) {
        return valor.toPlainString().replace('.', ',');
    }

    /** Protege contra injeção de fórmulas no Excel e escapa aspas e ";". */
    private static String texto(String valor) {
        String seguro = valor.matches("^[=+\\-@].*") ? "'" + valor : valor;
        if (seguro.contains(";") || seguro.contains("\"")) {
            return "\"" + seguro.replace("\"", "\"\"") + "\"";
        }
        return seguro;
    }
}
