package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import dev.barboza.cofre.Cenario.Pessoa;
import dev.barboza.cofre.dominio.ContaPoupanca;
import dev.barboza.cofre.dominio.ContaRepository;
import dev.barboza.cofre.dominio.LancamentoRepository;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.pix.PixService;
import dev.barboza.cofre.pix.TipoChavePix;
import dev.barboza.cofre.salario.Cnpj;
import dev.barboza.cofre.salario.PortabilidadeSalario;
import dev.barboza.cofre.salario.PortabilidadeSalario.Situacao;
import dev.barboza.cofre.salario.SalarioService;
import dev.barboza.cofre.servico.ContaService;

/** Traga seu salário: portabilidade (situação pelo tempo), crédito no dia do pagamento e Pague-se primeiro. */
@TesteIntegrado
class SalarioTest {

    private static final String CNPJ = "11.222.333/0001-81";

    @Autowired
    Cenario cenario;

    @Autowired
    SalarioService salario;

    @Autowired
    ContaService contas;

    @Autowired
    ContaRepository repositorio;

    @Autowired
    LancamentoRepository lancamentos;

    @Autowired
    PixService pix;

    @Autowired
    RelogioFixo.Ajustavel relogio;

    @AfterEach
    void voltar() {
        relogio.voltar();
    }

    private static BigDecimal v(String valor) {
        return new BigDecimal(valor).setScale(2);
    }

    private SalarioService.Pedido pedido(int dia) {
        return new SalarioService.Pedido("341", "Clínica Sorriso Ltda", CNPJ, v("4200"), dia);
    }

    @Test
    void cnpjValidadoPelosDigitosVerificadores() {
        assertThat(Cnpj.validar(CNPJ)).isEqualTo("11222333000181");
        assertThat(Cnpj.formatar("48291573000148")).isEqualTo("48.291.573/0001-48");
    }

    @ParameterizedTest
    @ValueSource(strings = {"11.222.333/0001-82", "11111111111111", "123", ""})
    void cnpjInvalidoERecusado(String cnpj) {
        assertThatThrownBy(() -> Cnpj.validar(cnpj)).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void portabilidadeAndaPeloTempoECancelaAntesDeConcluir() {
        Pessoa ana = cenario.cliente("Ana Porta", "100", "0");
        PortabilidadeSalario p = salario.solicitar(ana.usuario(), pedido(5));
        Instant inicio = p.getSolicitadaEm();
        assertThat(p.getProtocolo()).matches("PS\\d{14}");
        assertThat(p.situacao(inicio)).isEqualTo(Situacao.SOLICITADA);
        assertThat(p.situacao(inicio.plus(Duration.ofHours(25)))).isEqualTo(Situacao.EM_ANALISE);
        assertThat(p.situacao(inicio.plus(Duration.ofDays(3)))).isEqualTo(Situacao.CONCLUIDA);
        assertThat(p.etapas(inicio.plus(Duration.ofHours(25)))).extracting(PortabilidadeSalario.Etapa::feita)
                .containsExactly(true, true, false);

        assertThatThrownBy(() -> salario.solicitar(ana.usuario(), pedido(5)))
                .isInstanceOf(OperacaoInvalidaException.class).hasMessageContaining("já tem uma portabilidade");
        salario.cancelar(ana.usuario(), p.getId());
        assertThat(p.situacao(inicio.plus(Duration.ofDays(10)))).isEqualTo(Situacao.CANCELADA);
        salario.solicitar(ana.usuario(), pedido(5));   // depois de cancelar, pode pedir de novo
    }

    @Test
    void naoCancelaDepoisDeConcluida() {
        Pessoa ana = cenario.cliente("Ana Concluida", "100", "0");
        PortabilidadeSalario p = salario.solicitar(ana.usuario(), pedido(5));
        relogio.ajustar(p.getSolicitadaEm().plus(Duration.ofDays(4)));
        assertThatThrownBy(() -> salario.cancelar(ana.usuario(), p.getId()))
                .isInstanceOf(OperacaoInvalidaException.class).hasMessageContaining("já foi concluída");
    }

    @Test
    void dadosInvalidosSaoRecusados() {
        Pessoa ana = cenario.cliente("Ana Dados", "100", "0");
        assertThatThrownBy(() -> salario.solicitar(ana.usuario(), new SalarioService.Pedido("999", "X Ltda", CNPJ, v("100"), 5)))
                .hasMessage("Escolha o banco onde o salário cai hoje.");
        assertThatThrownBy(() -> salario.solicitar(ana.usuario(), new SalarioService.Pedido("341", "X Ltda", CNPJ, v("100"), 32)))
                .hasMessage("O dia do pagamento vai de 1 a 31.");
        assertThatThrownBy(() -> salario.solicitar(ana.usuario(), new SalarioService.Pedido("341", "X Ltda", "123", v("100"), 5)))
                .hasMessage("CNPJ do empregador inválido.");
    }

    @Test
    void salarioCaiNoDiaDoPagamentoSoDepoisDeConcluidaEUmaVezPorMes() {
        Pessoa ana = cenario.cliente("Ana Salario", "0", "0");   // pedido em 26/09 (relógio dos testes)
        salario.solicitar(ana.usuario(), pedido(5));
        assertThat(salario.creditarSalarios(LocalDate.parse("2026-09-28"), Instant.parse("2026-09-28T12:00:00Z"))).isZero();
        assertThat(salario.creditarSalarios(LocalDate.parse("2026-10-05"), Instant.parse("2026-10-05T12:00:00Z"))).isEqualTo(1);
        assertThat(salario.creditarSalarios(LocalDate.parse("2026-10-05"), Instant.parse("2026-10-05T13:00:00Z"))).isZero();
        assertThat(repositorio.findByNumero(ana.conta().getNumero()).orElseThrow().getSaldo()).isEqualTo(v("4200"));
        var resumo = salario.resumo(ana.usuario());
        assertThat(resumo.mesesRecebidos()).isEqualTo(1);
        assertThat(resumo.salariosRecebidos()).isEqualTo(v("4200"));
    }

    @Test
    void pagueSePrimeiroGuardaAPorcentagemNaPoupancaAssimQueOSalarioCai() {
        Pessoa ana = cenario.cliente("Ana Guarda Salario", "0", "1000");
        assertThatThrownBy(() -> salario.definirReserva(ana.usuario(), 10))
                .hasMessageContaining("Abra uma poupança");
        ContaPoupanca poupanca = contas.abrirMinhaPoupanca(ana.usuario());
        salario.definirReserva(ana.usuario(), 15);
        assertThatThrownBy(() -> salario.definirReserva(ana.usuario(), 51)).hasMessage("Escolha de 0% a 50% do salário.");

        salario.solicitar(ana.usuario(), pedido(5));
        salario.creditarSalarios(LocalDate.parse("2026-10-05"), Instant.parse("2026-10-05T12:00:00Z"));
        assertThat(repositorio.findByNumero(poupanca.getNumero()).orElseThrow().getSaldo()).isEqualTo(v("630"));   // 15% de 4.200
        assertThat(repositorio.findByNumero(ana.conta().getNumero()).orElseThrow().getSaldo()).isEqualTo(v("3570"));
    }

    @Test
    void pagueSePrimeiroTambemValeParaPixComMensagemDeSalario() {
        Pessoa empresa = cenario.cliente("Empresa Pagadora", "10000", "0");
        Pessoa ana = cenario.cliente("Ana Pix Salario", "0", "0");
        cenario.chave(ana, TipoChavePix.EMAIL, "ana.salario@teste.dev");
        ContaPoupanca poupanca = contas.abrirMinhaPoupanca(ana.usuario());
        salario.definirReserva(ana.usuario(), 10);
        pix.enviar(empresa.usuario(), empresa.conta().getNumero(), "ana.salario@teste.dev", v("3000"), "Salário de setembro", null, true);
        pix.enviar(empresa.usuario(), empresa.conta().getNumero(), "ana.salario@teste.dev", v("100"), "Almoço", null, true);
        assertThat(repositorio.findByNumero(poupanca.getNumero()).orElseThrow().getSaldo()).isEqualTo(v("300"));
        assertThat(lancamentos.countByContaIdAndTipo(ana.conta().getId(), TipoLancamento.TRANSFERENCIA_ENVIADA)).isEqualTo(1);
    }
}
