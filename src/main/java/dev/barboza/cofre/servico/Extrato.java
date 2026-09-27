package dev.barboza.cofre.servico;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.Lancamento;

/** Extrato de um período: saldo no início, lançamentos, totais e saldo no fim. */
public record Extrato(
        Conta conta,
        LocalDate de,
        LocalDate ate,
        BigDecimal saldoInicial,
        List<Lancamento> lancamentos) {

    public BigDecimal entradas() {
        return lancamentos.stream().filter(l -> l.getTipo().credito())
                .map(Lancamento::getValor).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public BigDecimal saidas() {
        return lancamentos.stream().filter(l -> !l.getTipo().credito())
                .map(Lancamento::getValor).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public BigDecimal saldoFinal() {
        return lancamentos.isEmpty() ? saldoInicial : lancamentos.getLast().getSaldoApos();
    }
}
