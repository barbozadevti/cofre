package dev.barboza.cofre.servico;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.caixinha.CaixinhaRepository;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.Lancamento;
import dev.barboza.cofre.dominio.LancamentoRepository;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.seguranca.UsuarioLogado;

/** Dados da tela inicial do cliente: saldos, cheque especial, caixinhas e entradas x saídas por mês. */
@Service
public class PainelService {

    /** Movimentos internos (caixinha e abertura) não contam como "entrou" ou "saiu" no gráfico. */
    private static final Set<TipoLancamento> FORA_DO_GRAFICO = EnumSet.of(TipoLancamento.ABERTURA,
            TipoLancamento.CAIXINHA_GUARDADO, TipoLancamento.CAIXINHA_RESGATADO);

    private final ContaService contas;
    private final LancamentoRepository lancamentos;
    private final CaixinhaRepository caixinhas;
    private final Clock relogio;

    public PainelService(ContaService contas, LancamentoRepository lancamentos, CaixinhaRepository caixinhas, Clock relogio) {
        this.contas = contas;
        this.lancamentos = lancamentos;
        this.caixinhas = caixinhas;
        this.relogio = relogio;
    }

    public record Mes(String mes, BigDecimal entradas, BigDecimal saidas) {
    }

    public record Painel(List<Conta> contas, BigDecimal saldoTotal, BigDecimal limiteTotal, BigDecimal limiteEmUso,
            BigDecimal emCaixinhas, List<Mes> meses) {
    }

    @Transactional(readOnly = true)
    public Painel painel(UsuarioLogado quem) {
        Acesso.exigirCliente(quem);
        List<Conta> minhas = contas.contasVisiveis(quem);
        BigDecimal saldo = BigDecimal.ZERO.setScale(2);
        BigDecimal limite = BigDecimal.ZERO.setScale(2);
        BigDecimal uso = BigDecimal.ZERO.setScale(2);
        BigDecimal guardado = BigDecimal.ZERO.setScale(2);
        for (Conta c : minhas) {
            saldo = saldo.add(c.getSaldo());
            limite = limite.add(c.getLimite());
            uso = uso.add(c.usoDoLimite());
            guardado = guardado.add(caixinhas.totalDaConta(c.getId()));
        }
        return new Painel(minhas, saldo, limite, uso, guardado.setScale(2), meses(minhas));
    }

    private List<Mes> meses(List<Conta> minhas) {
        YearMonth atual = YearMonth.now(relogio.withZone(ContaService.FUSO));
        YearMonth primeiro = atual.minusMonths(5);
        Map<YearMonth, BigDecimal[]> porMes = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            porMes.put(primeiro.plusMonths(i), new BigDecimal[] {BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2)});
        }
        if (!minhas.isEmpty()) {
            List<Long> ids = minhas.stream().map(Conta::getId).toList();
            for (Lancamento l : lancamentos.findByContaIdInAndDataHoraGreaterThanEqualOrderByDataHoraAsc(ids,
                    primeiro.atDay(1).atStartOfDay(ContaService.FUSO).toInstant())) {
                if (FORA_DO_GRAFICO.contains(l.getTipo())) {
                    continue;
                }
                BigDecimal[] totais = porMes.get(YearMonth.from(l.getDataHora().atZone(ContaService.FUSO)));
                if (totais != null) {
                    int i = l.getTipo().credito() ? 0 : 1;
                    totais[i] = totais[i].add(l.getValor());
                }
            }
        }
        List<Mes> meses = new ArrayList<>();
        porMes.forEach((mes, t) -> meses.add(new Mes(mes.toString(), t[0], t[1])));
        return meses;
    }
}
