package dev.barboza.cofre.servico;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.dominio.ContaPoupanca;
import dev.barboza.cofre.dominio.ContaRepository;
import dev.barboza.cofre.dominio.LancamentoRepository;

/**
 * Credita o rendimento das poupanças que fazem aniversário no dia.
 * Idempotente: o id da transação é derivado da conta e do mês, então rodar duas vezes não paga duas vezes.
 */
@Service
public class RendimentoPoupanca {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("yyyyMM");

    private final ContaRepository contas;
    private final LancamentoRepository lancamentos;
    private final Clock relogio;

    public RendimentoPoupanca(ContaRepository contas, LancamentoRepository lancamentos, Clock relogio) {
        this.contas = contas;
        this.lancamentos = lancamentos;
        this.relogio = relogio;
    }

    /** Credita os rendimentos referentes ao {@code dia} e devolve quantas poupanças renderam. */
    @Transactional
    public int creditar(LocalDate dia) {
        return creditar(dia, relogio.instant());
    }

    @Transactional
    public int creditar(LocalDate dia, Instant quando) {
        int creditadas = 0;
        for (ContaPoupanca poupanca : contas.poupancasComSaldo()) {
            String id = "REND-" + poupanca.getNumero() + "-" + MES.format(dia);
            if (!poupanca.fazAniversario(dia, ContaService.FUSO) || lancamentos.existsByIdTransacao(id)) {
                continue;
            }
            var lancamento = poupanca.render(id, quando);
            if (lancamento.isPresent()) {
                lancamentos.save(lancamento.get());
                creditadas++;
            }
        }
        return creditadas;
    }
}
