package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.ContaNaoEncontradaException;
import dev.barboza.cofre.dominio.SaldoInsuficienteException;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.servico.ContaService;
import dev.barboza.cofre.servico.Extrato;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(RelogioFixo.class)
@Transactional
class ContaServiceTest {

    @Autowired
    ContaService servico;

    private static BigDecimal v(String valor) {
        return new BigDecimal(valor);
    }

    /** Instante no fuso de Brasília. */
    private static Instant em(String dataHora) {
        return java.time.LocalDateTime.parse(dataHora).atZone(ContaService.FUSO).toInstant();
    }

    @Test
    void numeraContasEmSequenciaComDigito() {
        Conta primeira = servico.abrir("Mario Andrade", null, v("10"));
        Conta segunda = servico.abrir("Ana Souza", "0412", v("0"));

        assertThat(primeira.getNumero()).isEqualTo("10001-3");
        assertThat(segunda.getNumero()).isEqualTo("10002-1");
        assertThat(servico.listar()).extracting(Conta::getNumero).containsExactly("10001-3", "10002-1");
    }

    @Test
    void buscaAceitaNumeroSemHifen() {
        Conta conta = servico.abrir("Mario Andrade", null, v("10"));
        assertThat(servico.buscar("100013").getId()).isEqualTo(conta.getId());
    }

    @Test
    void contaInexistente() {
        assertThatThrownBy(() -> servico.buscar("99999-7"))
                .isInstanceOf(ContaNaoEncontradaException.class)
                .hasMessage("Conta 99999-7 não encontrada.");
    }

    @Test
    void transferenciaAtualizaAsDuasContas() {
        Conta ana = servico.abrir("Ana Souza", null, v("500"));
        Conta joao = servico.abrir("João da Silva", null, v("0"));

        ContaService.Transferencia t = servico.transferir(ana.getNumero(), joao.getNumero(), v("125.50"));

        assertThat(t.origem().getSaldo()).isEqualTo(v("374.50"));
        assertThat(t.destino().getSaldo()).isEqualTo(v("125.50"));
        assertThat(servico.extrato(joao.getNumero(), null, null).lancamentos())
                .extracting(l -> l.getTipo())
                .containsExactly(TipoLancamento.ABERTURA, TipoLancamento.TRANSFERENCIA_RECEBIDA);
    }

    @Test
    void transferenciaSemSaldoNaoGeraLancamento() {
        Conta ana = servico.abrir("Ana Souza", null, v("50"));
        Conta joao = servico.abrir("João da Silva", null, v("0"));

        assertThatThrownBy(() -> servico.transferir(ana.getNumero(), joao.getNumero(), v("60")))
                .isInstanceOf(SaldoInsuficienteException.class);
        assertThat(servico.extrato(ana.getNumero(), null, null).lancamentos()).hasSize(1);
        assertThat(servico.extrato(joao.getNumero(), null, null).lancamentos()).hasSize(1);
    }

    @Test
    void extratoDoPeriodoTemSaldoAnteriorTotaisESaldoFinal() {
        Conta conta = servico.abrirEm("Mario Andrade", null, v("100"), em("2026-09-01T10:00"));
        servico.depositarEm(conta.getNumero(), v("50"), em("2026-09-10T09:00"));
        servico.sacarEm(conta.getNumero(), v("30"), em("2026-09-20T23:59"));
        servico.depositarEm(conta.getNumero(), v("5"), em("2026-09-21T00:00"));

        Extrato extrato = servico.extrato(conta.getNumero(), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20));

        assertThat(extrato.saldoInicial()).isEqualTo(v("100.00"));
        assertThat(extrato.lancamentos()).extracting(l -> l.getTipo())
                .containsExactly(TipoLancamento.DEPOSITO, TipoLancamento.SAQUE);
        assertThat(extrato.entradas()).isEqualTo(v("50.00"));
        assertThat(extrato.saidas()).isEqualTo(v("30.00"));
        assertThat(extrato.saldoFinal()).isEqualTo(v("120.00"));
    }

    @Test
    void extratoPadraoSaoOsUltimos30Dias() {
        Conta conta = servico.abrirEm("Mario Andrade", null, v("100"), em("2026-08-20T10:00"));
        servico.depositarEm(conta.getNumero(), v("10"), em("2026-08-27T23:59"));
        servico.depositarEm(conta.getNumero(), v("20"), em("2026-08-28T00:00"));

        Extrato extrato = servico.extrato(conta.getNumero(), null, null);

        assertThat(extrato.de()).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(extrato.ate()).isEqualTo(LocalDate.of(2026, 9, 26));
        assertThat(extrato.saldoInicial()).isEqualTo(v("110.00"));
        assertThat(extrato.lancamentos()).hasSize(1);
        assertThat(extrato.saldoFinal()).isEqualTo(v("130.00"));
    }

    @Test
    void extratoSemMovimentoMantemSaldo() {
        Conta conta = servico.abrirEm("Mario Andrade", null, v("100"), em("2026-01-05T10:00"));

        Extrato extrato = servico.extrato(conta.getNumero(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 26));

        assertThat(extrato.lancamentos()).isEmpty();
        assertThat(extrato.saldoFinal()).isEqualTo(v("100.00"));
    }

    @Test
    void extratoRecusaPeriodoInvertido() {
        Conta conta = servico.abrir("Mario Andrade", null, v("1"));
        assertThatThrownBy(() -> servico.extrato(conta.getNumero(), LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)))
                .hasMessage("A data inicial não pode ser depois da data final.");
    }

    @Test
    void encerraContaZerada() {
        Conta conta = servico.abrir("Mario Andrade", null, v("0"));
        assertThat(servico.encerrar(conta.getNumero()).getEncerradaEm()).isEqualTo(RelogioFixo.AGORA);
    }
}
