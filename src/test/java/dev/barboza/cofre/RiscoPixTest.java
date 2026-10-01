package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import dev.barboza.cofre.Cenario.Pessoa;
import dev.barboza.cofre.auditoria.AuditoriaRepository;
import dev.barboza.cofre.auditoria.EventoAuditoria;
import dev.barboza.cofre.dominio.ContaRepository;
import dev.barboza.cofre.pix.ConfirmacaoDeRiscoException;
import dev.barboza.cofre.pix.PixService;
import dev.barboza.cofre.pix.RiscoPix;
import dev.barboza.cofre.pix.TipoChavePix;

/** Antifraude do Pix: cada fator da nota de risco, a retenção e a confirmação reforçada. */
@TesteIntegrado
class RiscoPixTest {

    @Autowired
    Cenario cenario;

    @Autowired
    PixService pix;

    @Autowired
    RiscoPix risco;

    @Autowired
    ContaRepository contas;

    @Autowired
    AuditoriaRepository auditoria;

    @Autowired
    RelogioFixo.Ajustavel relogio;

    @AfterEach
    void voltarRelogio() {
        relogio.voltar();
    }

    private static BigDecimal v(String valor) {
        return new BigDecimal(valor).setScale(2);
    }

    private Pessoa destino(String nome, String email) {
        Pessoa p = cenario.cliente(nome, "0", "0");
        cenario.chave(p, TipoChavePix.EMAIL, email);
        return p;
    }

    private RiscoPix.Avaliacao avaliar(Pessoa de, Pessoa para, String valor) {
        return risco.avaliar(contas.findByNumero(de.conta().getNumero()).orElseThrow(),
                contas.findByNumero(para.conta().getNumero()).orElseThrow(), v(valor), relogio.instant());
    }

    @Test
    void pixDeDiaParaQuemJaRecebeuEValorComumNaoTemRisco() {
        Pessoa ana = cenario.cliente("Ana Rotina", "5000", "0");
        Pessoa bia = destino("Bia Amiga", "bia.risco1@teste.dev");
        relogio.ajustar(Instant.parse("2026-11-10T15:00:00Z"));   // 12h, contas com mais de 30 dias
        for (int i = 0; i < 3; i++) {
            pix.enviar(ana.usuario(), ana.conta().getNumero(), "bia.risco1@teste.dev", v("100"), null, null, true);
            relogio.ajustar(relogio.instant().plusSeconds(3600));
        }
        RiscoPix.Avaliacao a = avaliar(ana, bia, "150");
        assertThat(a.pontuacao()).isZero();
        assertThat(a.fatores()).isEmpty();
    }

    @Test
    void golpeTipicoSomaOsFatoresComOsMotivos() {
        Pessoa ana = cenario.cliente("Ana Alvo", "5000", "0");
        Pessoa conhecida = destino("Carla Conhecida", "carla.risco@teste.dev");
        relogio.ajustar(Instant.parse("2026-11-10T15:00:00Z"));
        for (int i = 0; i < 3; i++) {   // média de R$ 100 em Pix
            pix.enviar(ana.usuario(), ana.conta().getNumero(), "carla.risco@teste.dev", v("100"), null, null, true);
        }
        Pessoa golpista = destino("Conta Laranja", "laranja@teste.dev");   // aberta "hoje" no relógio do cenário
        relogio.ajustar(Instant.parse("2026-11-10T15:05:00Z"));
        RiscoPix.Avaliacao a = avaliar(ana, golpista, "4500");
        assertThat(a.fatores()).extracting(RiscoPix.Fator::codigo).containsExactlyInAnyOrder(
                "DESTINATARIO_NOVO", "VALOR_FORA_DO_PADRAO", "RAJADA", "ESVAZIA_A_CONTA");
        assertThat(a.pontuacao()).isEqualTo(100);   // 30 + 35 + 20 + 15
        assertThat(a.exigeConfirmacao()).isTrue();
        assertThat(a.fatores().getFirst().descricao()).contains("Conta Laranja");
        assertThat(conhecida).isNotNull();
    }

    @Test
    void noturnoEContaDeDestinoRecentePesam() {
        Pessoa ana = cenario.cliente("Ana Noite", "5000", "0");
        Pessoa novo = destino("Novo Recebedor", "novo.risco@teste.dev");
        relogio.ajustar(Instant.parse("2026-09-27T01:00:00Z"));   // 22h em Brasília
        RiscoPix.Avaliacao a = avaliar(ana, novo, "50");
        assertThat(a.fatores()).extracting(RiscoPix.Fator::codigo)
                .containsExactlyInAnyOrder("DESTINATARIO_NOVO", "HORARIO_NOTURNO", "CONTA_DESTINO_RECENTE");
        assertThat(a.pontuacao()).isEqualTo(70);
    }

    @Test
    void pixRetidoNaoDebitaFicaNaAuditoriaEConfirmadoSai() {
        Pessoa ana = cenario.cliente("Ana Retida", "2000", "0");
        destino("Recebedor Suspeito", "suspeito@teste.dev");
        relogio.ajustar(Instant.parse("2026-09-27T01:00:00Z"));
        String numero = ana.conta().getNumero();

        assertThatThrownBy(() -> pix.enviar(ana.usuario(), numero, "suspeito@teste.dev", v("300"), null, null))
                .isInstanceOfSatisfying(ConfirmacaoDeRiscoException.class,
                        e -> assertThat(e.getAvaliacao().pontuacao()).isEqualTo(70));
        assertThat(contas.findByNumero(numero).orElseThrow().getSaldo()).isEqualTo(v("2000"));
        EventoAuditoria retido = auditoria.findAllByOrderByDataHoraDescIdDesc(PageRequest.of(0, 1)).getContent().getFirst();
        assertThat(retido.getAcao()).isEqualTo("PIX_RETIDO_RISCO");

        pix.enviar(ana.usuario(), numero, "suspeito@teste.dev", v("300"), null, null, true);
        assertThat(contas.findByNumero(numero).orElseThrow().getSaldo()).isEqualTo(v("1700"));
        assertThat(auditoria.findAllByOrderByDataHoraDescIdDesc(PageRequest.of(0, 2)).getContent())
                .extracting(EventoAuditoria::getAcao).contains("PIX_RISCO_CONFIRMADO");
    }
}
