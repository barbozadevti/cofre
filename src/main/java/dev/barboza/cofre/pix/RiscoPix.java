package dev.barboza.cofre.pix;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.Dinheiro;
import dev.barboza.cofre.dominio.LancamentoRepository;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.servico.ContaService;

/**
 * Nota de risco do Pix, de 0 a 100, com os motivos. Não é uma caixa-preta: cada fator é uma regra
 * simples, com peso fixo, que o cliente e o analista conseguem entender. Os fatores refletem os golpes
 * mais comuns (falso parente, falsa central, sequestro relâmpago, conta "laranja" recém-aberta).
 * <p>
 * Acima de {@value #LIMIAR_DE_CONFIRMACAO} pontos, o Pix só sai com uma confirmação reforçada do cliente.
 */
@Service
public class RiscoPix {

    public static final int LIMIAR_DE_CONFIRMACAO = 60;
    static final Duration HISTORICO = Duration.ofDays(90);
    static final Duration RAJADA = Duration.ofMinutes(10);

    private final LancamentoRepository lancamentos;

    public RiscoPix(LancamentoRepository lancamentos) {
        this.lancamentos = lancamentos;
    }

    public record Fator(String codigo, String descricao, int pontos) {
    }

    public record Avaliacao(int pontuacao, List<Fator> fatores) {

        public boolean exigeConfirmacao() {
            return pontuacao >= LIMIAR_DE_CONFIRMACAO;
        }

        public String resumo() {
            return pontuacao + " pontos: " + String.join(", ", fatores.stream().map(Fator::codigo).toList());
        }
    }

    @Transactional(readOnly = true)
    public Avaliacao avaliar(Conta origem, Conta destino, BigDecimal valor, Instant agora) {
        List<Fator> fatores = new ArrayList<>();

        boolean conhecido = lancamentos.existsByContaIdAndContraparteAndTipoIn(origem.getId(), destino.getNumero(),
                List.of(TipoLancamento.PIX_ENVIADO, TipoLancamento.TRANSFERENCIA_ENVIADA));
        if (!conhecido) {
            fatores.add(new Fator("DESTINATARIO_NOVO", "Primeira vez que você envia dinheiro para " + destino.getCliente().getNome() + ".", 30));
        }

        List<BigDecimal> anteriores = lancamentos.valores(origem.getId(), TipoLancamento.PIX_ENVIADO, agora.minus(HISTORICO), agora);
        if (anteriores.size() >= 3) {
            BigDecimal media = anteriores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(anteriores.size()), 2, RoundingMode.HALF_EVEN);
            if (valor.compareTo(media.multiply(BigDecimal.valueOf(3))) > 0) {
                fatores.add(new Fator("VALOR_FORA_DO_PADRAO", "Valor bem acima do seu costume: a média dos seus Pix nos últimos 90 dias é "
                        + Dinheiro.formatar(media) + ".", 35));
            }
        } else if (valor.compareTo(new BigDecimal("1000")) >= 0) {
            fatores.add(new Fator("VALOR_ALTO_SEM_HISTORICO", "Valor alto e pouco histórico de Pix para comparar.", 20));
        }

        int hora = agora.atZone(ContaService.FUSO).getHour();
        if (hora >= 20 || hora < 6) {
            fatores.add(new Fator("HORARIO_NOTURNO", "Envio à noite, horário mais visado por golpes.", 15));
        }

        if (destino.getAbertaEm().isAfter(agora.minus(Duration.ofDays(30)))) {
            fatores.add(new Fator("CONTA_DESTINO_RECENTE", "A conta de destino foi aberta há menos de 30 dias.", 25));
        }

        long recentes = lancamentos.valores(origem.getId(), TipoLancamento.PIX_ENVIADO, agora.minus(RAJADA), agora).size();
        if (recentes >= 3) {
            fatores.add(new Fator("RAJADA", recentes + " Pix enviados nos últimos 10 minutos.", 20));
        }

        if (origem.disponivel().signum() > 0 && valor.compareTo(origem.disponivel().multiply(new BigDecimal("0.8"))) >= 0) {
            fatores.add(new Fator("ESVAZIA_A_CONTA", "O Pix usa quase todo o dinheiro disponível na conta.", 15));
        }

        int total = Math.min(100, fatores.stream().mapToInt(Fator::pontos).sum());
        return new Avaliacao(total, List.copyOf(fatores));
    }
}
