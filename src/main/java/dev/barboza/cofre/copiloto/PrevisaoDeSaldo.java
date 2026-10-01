package dev.barboza.cofre.copiloto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Previsão do saldo para os próximos dias a partir do histórico: encontra o que se repete todo mês
 * (salário, aluguel, escola...) e projeta. É uma função pura, sem banco nem Spring, para poder ser
 * testada com números escritos à mão.
 * <p>
 * Regra de recorrência, simples e explicável: mesma descrição e mesmo sentido (entrada ou saída),
 * em pelo menos 2 dos últimos 3 meses, em dias do mês próximos (diferença de até 5 dias).
 */
public final class PrevisaoDeSaldo {

    public static final int DIAS = 30;
    static final int MESES_DE_HISTORICO = 3;
    static final int OCORRENCIAS_MINIMAS = 2;
    static final int TOLERANCIA_DE_DIAS = 5;

    private PrevisaoDeSaldo() {
    }

    /** Um movimento do extrato: valor positivo entra, negativo sai. */
    public record Movimento(LocalDate dia, String descricao, BigDecimal valor) {
    }

    /** Algo que se repete todo mês, com o dia e o valor médio. */
    public record Recorrencia(String descricao, int dia, BigDecimal valor, int meses) {
    }

    public record Evento(LocalDate dia, String descricao, BigDecimal valor, BigDecimal saldoApos) {
    }

    public record Ponto(LocalDate dia, BigDecimal saldo) {
    }

    public record Resultado(BigDecimal saldoAtual, List<Recorrencia> recorrencias, List<Evento> eventos,
            List<Ponto> pontos, BigDecimal menorSaldo, LocalDate diaDoMenorSaldo, LocalDate primeiroDiaNegativo) {

        public boolean vaiFicarNegativo() {
            return primeiroDiaNegativo != null;
        }
    }

    public static List<Recorrencia> recorrencias(List<Movimento> historico, LocalDate hoje) {
        YearMonth atual = YearMonth.from(hoje);
        YearMonth inicio = atual.minusMonths(MESES_DE_HISTORICO);
        Map<String, List<Movimento>> grupos = new LinkedHashMap<>();
        for (Movimento m : historico) {
            YearMonth mes = YearMonth.from(m.dia());
            if (mes.isBefore(inicio) || mes.isAfter(atual) || m.valor().signum() == 0) {
                continue;
            }
            String chave = (m.valor().signum() > 0 ? "+" : "-") + normalizar(m.descricao());
            grupos.computeIfAbsent(chave, k -> new ArrayList<>()).add(m);
        }
        List<Recorrencia> lista = new ArrayList<>();
        for (List<Movimento> grupo : grupos.values()) {
            Set<YearMonth> meses = new TreeSet<>();
            grupo.forEach(m -> meses.add(YearMonth.from(m.dia())));
            List<Integer> dias = grupo.stream().map(m -> m.dia().getDayOfMonth()).sorted().toList();
            if (meses.size() < OCORRENCIAS_MINIMAS || dias.getLast() - dias.getFirst() > TOLERANCIA_DE_DIAS) {
                continue;
            }
            BigDecimal soma = grupo.stream().map(Movimento::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal media = soma.divide(BigDecimal.valueOf(grupo.size()), 2, RoundingMode.HALF_EVEN);
            lista.add(new Recorrencia(grupo.getLast().descricao(), dias.get(dias.size() / 2), media, meses.size()));
        }
        lista.sort(Comparator.comparingInt(Recorrencia::dia));
        return lista;
    }

    /**
     * Projeta os próximos {@value #DIAS} dias. Uma recorrência que já aconteceu neste mês (no histórico)
     * não é contada de novo neste mês.
     */
    public static Resultado prever(List<Movimento> historico, BigDecimal saldoAtual, LocalDate hoje) {
        List<Recorrencia> recorrencias = recorrencias(historico, hoje);
        List<Evento> eventos = new ArrayList<>();
        List<Ponto> pontos = new ArrayList<>();
        BigDecimal saldo = saldoAtual;
        BigDecimal menor = saldoAtual;
        LocalDate diaDoMenor = hoje;
        LocalDate primeiroNegativo = saldoAtual.signum() < 0 ? hoje : null;
        for (int i = 1; i <= DIAS; i++) {
            LocalDate dia = hoje.plusDays(i);
            for (Recorrencia r : recorrencias) {
                int diaNoMes = Math.min(r.dia(), dia.lengthOfMonth());
                if (dia.getDayOfMonth() != diaNoMes || jaAconteceuNoMes(historico, r, dia)) {
                    continue;
                }
                saldo = saldo.add(r.valor());
                eventos.add(new Evento(dia, r.descricao(), r.valor(), saldo));
            }
            pontos.add(new Ponto(dia, saldo));
            if (saldo.compareTo(menor) < 0) {
                menor = saldo;
                diaDoMenor = dia;
            }
            if (saldo.signum() < 0 && primeiroNegativo == null) {
                primeiroNegativo = dia;
            }
        }
        return new Resultado(saldoAtual, recorrencias, eventos, pontos, menor, diaDoMenor, primeiroNegativo);
    }

    private static boolean jaAconteceuNoMes(List<Movimento> historico, Recorrencia r, LocalDate dia) {
        YearMonth mes = YearMonth.from(dia);
        String chave = normalizar(r.descricao());
        return historico.stream().anyMatch(m -> YearMonth.from(m.dia()).equals(mes)
                && m.valor().signum() == r.valor().signum() && normalizar(m.descricao()).equals(chave));
    }

    static String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", "").trim().replaceAll("\\s+", " ");
    }
}
