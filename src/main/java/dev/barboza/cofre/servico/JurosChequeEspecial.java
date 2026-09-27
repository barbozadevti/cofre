package dev.barboza.cofre.servico;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.ContaRepository;
import dev.barboza.cofre.dominio.LancamentoRepository;

/**
 * Cobra, uma vez por dia, os juros sobre o saldo negativo (cheque especial).
 * Idempotente: o id da transação é derivado da conta e do dia, então rodar duas vezes não cobra duas vezes.
 */
@Service
public class JurosChequeEspecial {

    /** 8% ao mês, cobrados por dia (8% / 30). Abaixo do teto de 8% a.m. definido pelo CMN em 2020. */
    public static final BigDecimal TAXA_MENSAL = new BigDecimal("0.08");
    static final BigDecimal TAXA_DIARIA = TAXA_MENSAL.divide(BigDecimal.valueOf(30), MathContext.DECIMAL64);

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DIA_LEGIVEL = DateTimeFormatter.ofPattern("dd/MM");

    private final ContaRepository contas;
    private final LancamentoRepository lancamentos;
    private final Clock relogio;

    public JurosChequeEspecial(ContaRepository contas, LancamentoRepository lancamentos, Clock relogio) {
        this.contas = contas;
        this.lancamentos = lancamentos;
        this.relogio = relogio;
    }

    public static BigDecimal jurosDoDia(BigDecimal saldoNegativo) {
        return saldoNegativo.negate().multiply(TAXA_DIARIA).setScale(2, RoundingMode.HALF_EVEN);
    }

    /** Cobra os juros referentes ao {@code dia} e devolve quantas contas foram cobradas. */
    @Transactional
    public int cobrar(LocalDate dia) {
        return cobrar(dia, relogio.instant());
    }

    @Transactional
    public int cobrar(LocalDate dia, java.time.Instant quando) {
        int cobradas = 0;
        for (Conta conta : contas.usandoChequeEspecial()) {
            String id = "JUROS-" + conta.getNumero() + "-" + DIA.format(dia);
            BigDecimal valor = jurosDoDia(conta.getSaldo());
            if (valor.signum() <= 0 || lancamentos.existsByIdTransacao(id)) {
                continue;
            }
            lancamentos.save(conta.cobrarJuros(valor, id, "Referente a " + DIA_LEGIVEL.format(dia) + " (8% a.m.)",
                    quando));
            cobradas++;
        }
        return cobradas;
    }
}
