package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.barboza.cofre.Cenario.Pessoa;
import dev.barboza.cofre.copiloto.CopilotoService;
import dev.barboza.cofre.copiloto.PrevisaoDeSaldo;
import dev.barboza.cofre.copiloto.PrevisaoDeSaldo.Movimento;
import dev.barboza.cofre.dominio.ContaPoupanca;
import dev.barboza.cofre.dominio.ContaRepository;
import dev.barboza.cofre.dominio.LancamentoRepository;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.pix.TipoChavePix;
import dev.barboza.cofre.servico.ContaService;

/** Copiloto financeiro: previsão de saldo (função pura) e Escudo de juros (integrado). */
class CopilotoTest {

    private static BigDecimal v(String valor) {
        return new BigDecimal(valor).setScale(2);
    }

    private static Movimento m(String dia, String descricao, String valor) {
        return new Movimento(LocalDate.parse(dia), descricao, v(valor));
    }

    /** Três meses de uma rotina simples: salário no dia 5, aluguel no dia 10, mercado em dias variados. */
    private static List<Movimento> rotina() {
        List<Movimento> lista = new ArrayList<>();
        for (String mes : List.of("2026-06", "2026-07", "2026-08")) {
            lista.add(m(mes + "-05", "Salário · Helena", "3000"));
            lista.add(m(mes + "-10", "Aluguel · Imobiliária", "-1800"));
        }
        lista.add(m("2026-06-12", "Mercado", "-300"));
        lista.add(m("2026-07-25", "Mercado", "-250"));   // dias muito diferentes: não é recorrente
        lista.add(m("2026-08-15", "Presente", "-200"));   // só uma vez: não é recorrente
        return lista;
    }

    @Nested
    class Previsao {

        @Test
        void reconheceSoOQueSeRepeteNoMesmoPeriodoDoMes() {
            var recorrencias = PrevisaoDeSaldo.recorrencias(rotina(), LocalDate.parse("2026-09-01"));
            assertThat(recorrencias).extracting(PrevisaoDeSaldo.Recorrencia::descricao)
                    .containsExactly("Salário · Helena", "Aluguel · Imobiliária");
            assertThat(recorrencias.getFirst().dia()).isEqualTo(5);
            assertThat(recorrencias.getFirst().valor()).isEqualTo(v("3000"));
            assertThat(recorrencias.getFirst().meses()).isEqualTo(3);
        }

        @Test
        void projetaOsProximos30DiasEApontaODiaNegativo() {
            // Hoje 01/09, saldo 500: dia 05 entra 3000 (3500), dia 10 sai 1800 (1700). Nunca negativo.
            var p = PrevisaoDeSaldo.prever(rotina(), v("500"), LocalDate.parse("2026-09-01"));
            assertThat(p.pontos()).hasSize(30);
            assertThat(p.eventos()).extracting(PrevisaoDeSaldo.Evento::saldoApos).containsExactly(v("3500"), v("1700"));
            assertThat(p.vaiFicarNegativo()).isFalse();
            assertThat(p.menorSaldo()).isEqualTo(v("500"));

            // Hoje 06/09, salário de setembro já entrou (no histórico) e saldo 1000: dia 10 o aluguel deixa -800.
            List<Movimento> comSetembro = new ArrayList<>(rotina());
            comSetembro.add(m("2026-09-05", "Salário · Helena", "3000"));
            var negativo = PrevisaoDeSaldo.prever(comSetembro, v("1000"), LocalDate.parse("2026-09-06"));
            assertThat(negativo.primeiroDiaNegativo()).isEqualTo(LocalDate.parse("2026-09-10"));
            assertThat(negativo.menorSaldo()).isEqualTo(v("-800"));
            // O salário de outubro (dia 05) volta a entrar dentro da janela de 30 dias.
            assertThat(negativo.eventos()).extracting(PrevisaoDeSaldo.Evento::dia)
                    .containsExactly(LocalDate.parse("2026-09-10"), LocalDate.parse("2026-10-05"));
        }

        @Test
        void diaDoMesQueNaoExisteViraOUltimoDia() {
            List<Movimento> lista = List.of(m("2026-07-31", "Fatura", "-100"), m("2026-08-31", "Fatura", "-100"));
            var p = PrevisaoDeSaldo.prever(lista, v("0"), LocalDate.parse("2026-09-01"));
            assertThat(p.eventos()).extracting(PrevisaoDeSaldo.Evento::dia).containsExactly(LocalDate.parse("2026-09-30"));
        }

        @Test
        void semHistoricoOSaldoFicaParado() {
            var p = PrevisaoDeSaldo.prever(List.of(), v("-50"), LocalDate.parse("2026-09-01"));
            assertThat(p.recorrencias()).isEmpty();
            assertThat(p.primeiroDiaNegativo()).isEqualTo(LocalDate.parse("2026-09-01"));
        }
    }

    @Nested
    @TesteIntegrado
    class Escudo {

        @Autowired
        Cenario cenario;

        @Autowired
        ContaService contas;

        @Autowired
        CopilotoService copiloto;

        @Autowired
        ContaRepository repositorio;

        @Autowired
        LancamentoRepository lancamentos;

        @Autowired
        dev.barboza.cofre.pix.PixService pix;

        private ContaPoupanca poupancaCom(Pessoa p, String valor) {
            ContaPoupanca poupanca = contas.abrirMinhaPoupanca(p.usuario());
            contas.transferir(p.usuario(), p.conta().getNumero(), poupanca.getNumero(), v(valor), null, null);
            return poupanca;
        }

        @Test
        void semPoupancaNaoLigaOEscudo() {
            Pessoa ana = cenario.cliente("Ana Sem Reserva", "100.00", "500");
            assertThatThrownBy(() -> copiloto.ativarEscudo(ana.usuario(), ana.conta().getNumero()))
                    .isInstanceOf(OperacaoInvalidaException.class).hasMessage("Abra uma poupança para usar o Escudo de juros.");
        }

        @Test
        void pixQueDeixariaAContaNegativaECobertoNaHoraPelaPoupanca() {
            Pessoa ana = cenario.cliente("Ana Protegida", "1000.00", "1000");
            Pessoa bia = cenario.cliente("Bia Recebe", "0", "0");
            cenario.chave(bia, TipoChavePix.EMAIL, "bia.escudo@teste.dev");
            ContaPoupanca poupanca = poupancaCom(ana, "600");   // corrente fica com 400
            copiloto.ativarEscudo(ana.usuario(), ana.conta().getNumero());

            pix.enviar(ana.usuario(), ana.conta().getNumero(), "bia.escudo@teste.dev", v("700"), null, null);

            assertThat(repositorio.findByNumero(ana.conta().getNumero()).orElseThrow().getSaldo()).isEqualTo(v("0"));
            assertThat(repositorio.findByNumero(poupanca.getNumero()).orElseThrow().getSaldo()).isEqualTo(v("300"));
            assertThat(lancamentos.countByContaIdAndTipo(ana.conta().getId(), TipoLancamento.ESCUDO_COBERTURA)).isEqualTo(1);

            var c = copiloto.copiloto(ana.usuario(), ana.conta().getNumero());
            assertThat(c.escudo().totalCoberto()).isEqualTo(v("300"));
            assertThat(c.escudo().economiaEstimada()).isEqualTo(v("22.50"));   // 300 x (8% - 0,5%)
        }

        @Test
        void poupancaInsuficienteCobreOQueDaEORestoUsaOLimite() {
            Pessoa ana = cenario.cliente("Ana Parcial", "200.00", "1000");
            ContaPoupanca poupanca = poupancaCom(ana, "100");   // corrente 100, poupança 100
            copiloto.ativarEscudo(ana.usuario(), ana.conta().getNumero());
            contas.sacarEm(ana.conta().getNumero(), v("400"), "Saque", RelogioFixo.AGORA);   // -300, cobre 100
            assertThat(repositorio.findByNumero(ana.conta().getNumero()).orElseThrow().getSaldo()).isEqualTo(v("-200"));
            assertThat(repositorio.findByNumero(poupanca.getNumero()).orElseThrow().getSaldo()).isEqualTo(v("0"));
        }

        @Test
        void ligarOEscudoComAContaNegativaCobreNaHora() {
            Pessoa ana = cenario.cliente("Ana Ja Negativa", "100.00", "1000");
            poupancaCom(ana, "100");
            contas.sacarEm(ana.conta().getNumero(), v("250"), "Saque", RelogioFixo.AGORA);   // -250, escudo desligado
            copiloto.ativarEscudo(ana.usuario(), ana.conta().getNumero());
            assertThat(repositorio.findByNumero(ana.conta().getNumero()).orElseThrow().getSaldo()).isEqualTo(v("-150"));
        }

        @Test
        void guardarNaPoupancaUsandoOLimiteNaoEDesfeitoPeloEscudo() {
            Pessoa ana = cenario.cliente("Ana Guarda", "100.00", "1000");
            ContaPoupanca poupanca = poupancaCom(ana, "50");
            copiloto.ativarEscudo(ana.usuario(), ana.conta().getNumero());
            contas.transferir(ana.usuario(), ana.conta().getNumero(), poupanca.getNumero(), v("200"), null, null);
            assertThat(repositorio.findByNumero(ana.conta().getNumero()).orElseThrow().getSaldo()).isEqualTo(v("-150"));
            assertThat(repositorio.findByNumero(poupanca.getNumero()).orElseThrow().getSaldo()).isEqualTo(v("250"));
        }

        @Test
        void desligadoNaoCobreERotinaDiariaCobreQuemLigou() {
            Pessoa ana = cenario.cliente("Ana Rotina", "100.00", "1000");
            poupancaCom(ana, "100");
            copiloto.ativarEscudo(ana.usuario(), ana.conta().getNumero());
            copiloto.desativarEscudo(ana.usuario(), ana.conta().getNumero());
            contas.sacarEm(ana.conta().getNumero(), v("50"), "Saque", RelogioFixo.AGORA);
            assertThat(repositorio.findByNumero(ana.conta().getNumero()).orElseThrow().getSaldo()).isEqualTo(v("-50"));
            assertThat(copiloto.proteger(RelogioFixo.AGORA)).isZero();
            copiloto.ativarEscudo(ana.usuario(), ana.conta().getNumero());   // ao ligar, já cobre
            assertThat(repositorio.findByNumero(ana.conta().getNumero()).orElseThrow().getSaldo()).isEqualTo(v("0"));
            assertThatThrownBy(() -> copiloto.ativarEscudo(ana.usuario(), ana.conta().getNumero()))
                    .isInstanceOf(OperacaoInvalidaException.class);
        }

        @Test
        void copilotoAvisaQuandoNaoHaRecorrencias() {
            Pessoa ana = cenario.cliente("Ana Nova", "500.00", "0");
            var c = copiloto.copiloto(ana.usuario(), ana.conta().getNumero());
            assertThat(c.nivel()).isEqualTo(CopilotoService.Nivel.TRANQUILO);
            assertThat(c.escudo().disponivel()).isFalse();
        }
    }
}
