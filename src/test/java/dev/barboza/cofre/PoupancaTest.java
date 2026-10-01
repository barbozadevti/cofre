package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import dev.barboza.cofre.Cenario.Pessoa;
import dev.barboza.cofre.dominio.Cliente;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.ContaCorrente;
import dev.barboza.cofre.dominio.ContaPoupanca;
import dev.barboza.cofre.dominio.ContaRepository;
import dev.barboza.cofre.dominio.Cpf;
import dev.barboza.cofre.dominio.LancamentoRepository;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import dev.barboza.cofre.dominio.SaldoInsuficienteException;
import dev.barboza.cofre.dominio.TipoConta;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.cartao.CartaoService;
import dev.barboza.cofre.seguranca.Perfil;
import dev.barboza.cofre.servico.ContaService;
import dev.barboza.cofre.servico.GerenciaService;
import dev.barboza.cofre.servico.RendimentoPoupanca;

/** Conta poupança: herança e polimorfismo na conta, rendimento no aniversário e as regras do app. */
class PoupancaTest {

    private static final ZoneId FUSO = ContaService.FUSO;
    private static final Cliente ANA = new Cliente("Ana Souza", Cpf.completar("731640582"), "ana@teste.dev", null,
            Instant.parse("2026-01-01T12:00:00Z"));

    private static BigDecimal v(String valor) {
        return new BigDecimal(valor).setScale(2);
    }

    private static Instant em(String data) {
        return LocalDate.parse(data).atTime(10, 0).atZone(FUSO).toInstant();
    }

    @Nested
    class Dominio {

        @Test
        void poupancaNaoTemChequeEspecialEODisponivelESoOSaldo() {
            ContaPoupanca p = ContaPoupanca.abrir(20001, "0001", ANA, v("100"), em("2026-03-10")).conta();
            assertThat(p.getTipo()).isEqualTo(TipoConta.POUPANCA);
            assertThat(p.getLimite()).isEqualTo(v("0"));
            assertThat(p.disponivel()).isEqualTo(v("100"));
            assertThatThrownBy(() -> p.debitar(TipoLancamento.SAQUE, v("100.01"), null, null, "T1", em("2026-03-11"), true))
                    .isInstanceOf(SaldoInsuficienteException.class);
        }

        @Test
        void mesmaChamadaComportamentoDiferente() {
            List<Conta> contas = List.of(
                    ContaCorrente.abrir(20002, "0001", ANA, v("100"), em("2026-03-10")).conta(),
                    ContaPoupanca.abrir(20003, "0001", ANA, v("100"), em("2026-03-10")).conta());
            ((ContaCorrente) contas.get(0)).definirLimite(v("500"));
            assertThat(contas).extracting(Conta::disponivel).containsExactly(v("600"), v("100"));
        }

        @ParameterizedTest
        @CsvSource({"2026-03-10, 10", "2026-03-28, 28", "2026-03-29, 1", "2026-03-31, 1"})
        void aniversarioEODiaDeAberturaMasDe29a31ViraDia1(String abertura, int dia) {
            ContaPoupanca p = ContaPoupanca.abrir(20004, "0001", ANA, v("1"), em(abertura)).conta();
            assertThat(p.diaDeAniversario(FUSO)).isEqualTo(dia);
        }

        @ParameterizedTest
        @CsvSource({
                "2026-03-10, 2026-04-10, true",
                "2026-03-10, 2026-03-10, false",   // no dia da abertura ainda não rende
                "2026-03-10, 2026-04-11, false",
                "2026-03-10, 2026-07-10, true",
                "2026-03-30, 2026-04-01, false",   // data-base 01/04: primeiro rendimento em 01/05
                "2026-03-30, 2026-05-01, true"})
        void fazAniversarioSoDepoisDeUmMesDaDataBase(String abertura, String dia, boolean rende) {
            ContaPoupanca p = ContaPoupanca.abrir(20005, "0001", ANA, v("1"), em(abertura)).conta();
            assertThat(p.fazAniversario(LocalDate.parse(dia), FUSO)).isEqualTo(rende);
        }

        @Test
        void rendeMeioPorCentoComArredondamentoBancario() {
            ContaPoupanca p = ContaPoupanca.abrir(20006, "0001", ANA, v("1005"), em("2026-03-10")).conta();
            assertThat(p.rendimentoEstimado()).isEqualTo(v("5.02"));   // 5,025 -> 5,02 (HALF_EVEN)
            var lancamento = p.render("REND-1", em("2026-04-10")).orElseThrow();
            assertThat(lancamento.getTipo()).isEqualTo(TipoLancamento.RENDIMENTO_POUPANCA);
            assertThat(lancamento.getMensagem()).isEqualTo("0,5% sobre R$ 1.005,00");
            assertThat(p.getSaldo()).isEqualTo(v("1010.02"));
        }

        @Test
        void saldoPequenoDemaisOuZeroNaoRende() {
            ContaPoupanca p = ContaPoupanca.abrir(20007, "0001", ANA, v("0.99"), em("2026-03-10")).conta();
            assertThat(p.render("REND-2", em("2026-04-10"))).isEmpty();   // 0,99 x 0,5% = 0,00
        }

        @Test
        void encerrarACorrenteZeraOLimite() {
            ContaCorrente c = ContaCorrente.abrir(20008, "0001", ANA, v("0"), em("2026-03-10")).conta();
            c.definirLimite(v("700"));
            c.encerrar(em("2026-03-11"));
            assertThat(c.getLimite()).isEqualTo(v("0"));
        }
    }

    @Nested
    @TesteIntegrado
    class Servicos {

        @Autowired
        Cenario cenario;

        @Autowired
        ContaService contas;

        @Autowired
        RendimentoPoupanca rendimento;

        @Autowired
        GerenciaService gerencia;

        @Autowired
        CartaoService cartoes;

        @Autowired
        ContaRepository repositorio;

        @Autowired
        LancamentoRepository lancamentos;

        @Test
        void clienteAbrePoupancaPeloAppSoUmaVez() {
            Pessoa ana = cenario.cliente("Ana Poupadora", "1000.00", "0");
            ContaPoupanca poupanca = contas.abrirMinhaPoupanca(ana.usuario());
            assertThat(poupanca.getAgencia()).isEqualTo(ana.conta().getAgencia());
            assertThat(contas.contasVisiveis(ana.usuario())).extracting(Conta::getTipo)
                    .containsExactly(TipoConta.CORRENTE, TipoConta.POUPANCA);
            assertThatThrownBy(() -> contas.abrirMinhaPoupanca(ana.usuario()))
                    .isInstanceOf(OperacaoInvalidaException.class).hasMessage("Você já tem uma conta poupança.");
            assertThatThrownBy(() -> contas.abrirMinhaPoupanca(cenario.funcionario(Perfil.CAIXA)))
                    .isInstanceOf(OperacaoInvalidaException.class);
        }

        @Test
        void transferirDaCorrenteParaAPoupancaEDeVolta() {
            Pessoa ana = cenario.cliente("Ana Transfere", "1000.00", "0");
            ContaPoupanca poupanca = contas.abrirMinhaPoupanca(ana.usuario());
            contas.transferir(ana.usuario(), ana.conta().getNumero(), poupanca.getNumero(), v("300"), "Guardar", null);
            contas.transferir(ana.usuario(), poupanca.getNumero(), ana.conta().getNumero(), v("100"), null, null);
            assertThat(repositorio.findByNumero(poupanca.getNumero()).orElseThrow().getSaldo()).isEqualTo(v("200"));
            assertThatThrownBy(() -> contas.transferir(ana.usuario(), poupanca.getNumero(), ana.conta().getNumero(), v("200.01"), null, null))
                    .isInstanceOf(SaldoInsuficienteException.class);
        }

        @Test
        void rendimentoCreditaNoAniversarioUmaVezSo() {
            Pessoa ana = cenario.cliente("Ana Rende", "0", "0");
            ContaPoupanca poupanca = contas.abrirPoupancaPara(ana.cliente(), null, v("2000"), em("2026-08-15"));
            assertThat(rendimento.creditar(LocalDate.parse("2026-09-14"), em("2026-09-14"))).isZero();
            assertThat(rendimento.creditar(LocalDate.parse("2026-09-15"), em("2026-09-15"))).isEqualTo(1);
            assertThat(rendimento.creditar(LocalDate.parse("2026-09-15"), em("2026-09-15"))).isZero();   // idempotente
            assertThat(repositorio.findByNumero(poupanca.getNumero()).orElseThrow().getSaldo()).isEqualTo(v("2010.00"));
            assertThat(lancamentos.existsByIdTransacao("REND-" + poupanca.getNumero() + "-202609")).isTrue();
        }

        @Test
        void correnteNaoRendeEPoupancaNaoTemLimiteNemCartao() {
            Pessoa ana = cenario.cliente("Ana Regras", "500.00", "200");
            ContaPoupanca poupanca = contas.abrirMinhaPoupanca(ana.usuario());
            assertThat(rendimento.creditar(LocalDate.parse("2026-10-26"), em("2026-10-26"))).isZero();
            assertThatThrownBy(() -> gerencia.definirLimite(cenario.funcionario(Perfil.GERENTE), poupanca.getNumero(), v("100")))
                    .isInstanceOf(OperacaoInvalidaException.class).hasMessageContaining("poupança e não tem cheque especial");
            assertThatThrownBy(() -> cartoes.cartao(ana.usuario(), poupanca.getNumero()))
                    .isInstanceOf(OperacaoInvalidaException.class).hasMessage("O cartão de débito é da conta corrente.");
        }
    }
}
