package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.barboza.cofre.dominio.AcessoNegadoException;
import dev.barboza.cofre.dominio.ContaNaoEncontradaException;
import dev.barboza.cofre.dominio.RecursoNaoEncontradoException;
import dev.barboza.cofre.dominio.SaldoInsuficienteException;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.seguranca.Perfil;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.servico.Comprovante;
import dev.barboza.cofre.servico.ContaService;

@TesteIntegrado
class ContaServiceTest {

    @Autowired
    ContaService contas;

    @Autowired
    Cenario cenario;

    private static BigDecimal v(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    void clienteNaoEnxergaContaDeOutroCliente() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "100", "0");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "100", "0");

        assertThat(contas.contasVisiveis(ana.usuario())).extracting(c -> c.getNumero()).containsExactly(ana.conta().getNumero());
        // "Não encontrada" (404), e não "acesso negado": não revela que a conta existe.
        assertThatThrownBy(() -> contas.buscar(ana.usuario(), joao.conta().getNumero()))
                .isInstanceOf(ContaNaoEncontradaException.class);
        assertThatThrownBy(() -> contas.extrato(ana.usuario(), joao.conta().getNumero(), null, null))
                .isInstanceOf(ContaNaoEncontradaException.class);
    }

    @Test
    void clienteNaoTransfereDaContaDeOutro() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "100", "0");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "100", "0");

        assertThatThrownBy(() -> contas.transferir(ana.usuario(), joao.conta().getNumero(), ana.conta().getNumero(),
                v("10"), null, null)).isInstanceOf(ContaNaoEncontradaException.class);
        assertThat(joao.conta().getSaldo()).isEqualTo(v("100.00"));
    }

    @Test
    void transferenciaGeraComprovanteComCpfMascarado() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "500", "0");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "0", "0");

        Comprovante c = contas.transferir(ana.usuario(), ana.conta().getNumero(), joao.conta().getNumero(), v("125.50"),
                "Almoço", null);

        assertThat(c.tipo()).isEqualTo("TRANSFERENCIA");
        assertThat(c.valor()).isEqualTo(v("125.50"));
        assertThat(c.origem().nome()).isEqualTo("Ana Souza");
        assertThat(c.origem().cpfMascarado()).matches("\\*\\*\\*\\.\\d{3}\\.\\d{3}-\\*\\*");
        assertThat(c.destino().conta()).isEqualTo(joao.conta().getNumero());
        assertThat(c.mensagem()).isEqualTo("Almoço");
        assertThat(ana.conta().getSaldo()).isEqualTo(v("374.50"));
        assertThat(joao.conta().getSaldo()).isEqualTo(v("125.50"));
        // As duas pontas podem ver o comprovante; um terceiro não.
        assertThat(contas.comprovante(joao.usuario(), c.idTransacao()).valor()).isEqualTo(v("125.50"));
        Cenario.Pessoa bia = cenario.cliente("Beatriz Lima", "0", "0");
        assertThatThrownBy(() -> contas.comprovante(bia.usuario(), c.idTransacao()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void mesmaIdempotencyKeyNaoDuplicaTransferencia() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "500", "0");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "0", "0");

        Comprovante primeira = contas.transferir(ana.usuario(), ana.conta().getNumero(), joao.conta().getNumero(),
                v("100"), null, "clique-123");
        Comprovante repetida = contas.transferir(ana.usuario(), ana.conta().getNumero(), joao.conta().getNumero(),
                v("100"), null, "clique-123");

        assertThat(repetida.idTransacao()).isEqualTo(primeira.idTransacao());
        assertThat(ana.conta().getSaldo()).isEqualTo(v("400.00"));
        assertThat(joao.conta().getSaldo()).isEqualTo(v("100.00"));
    }

    @Test
    void transferenciaUsaChequeEspecialESemLimiteNaoMexeEmNada() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "50", "100");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "0", "0");

        contas.transferir(ana.usuario(), ana.conta().getNumero(), joao.conta().getNumero(), v("150"), null, null);
        assertThat(ana.conta().getSaldo()).isEqualTo(v("-100.00"));

        assertThatThrownBy(() -> contas.transferir(ana.usuario(), ana.conta().getNumero(), joao.conta().getNumero(),
                v("0.01"), null, null)).isInstanceOf(SaldoInsuficienteException.class);
        assertThat(joao.conta().getSaldo()).isEqualTo(v("150.00"));
    }

    @Test
    void funcionarioNaoFazTransferenciaPeloCliente() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "500", "0");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "0", "0");
        UsuarioLogado gerente = cenario.funcionario(Perfil.GERENTE);

        assertThatThrownBy(() -> contas.transferir(gerente, ana.conta().getNumero(), joao.conta().getNumero(), v("1"),
                null, null)).isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void caixaDepositaESacaEmEspecieMasClienteNao() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "100", "200");
        UsuarioLogado caixa = cenario.funcionario(Perfil.CAIXA);

        contas.depositarEmEspecie(caixa, ana.conta().getNumero(), v("50"));
        contas.sacarEmEspecie(caixa, ana.conta().getNumero(), v("300"));

        assertThat(ana.conta().getSaldo()).isEqualTo(v("-150.00"));
        assertThatThrownBy(() -> contas.depositarEmEspecie(ana.usuario(), ana.conta().getNumero(), v("1")))
                .isInstanceOf(AcessoNegadoException.class);
        assertThat(contas.extrato(caixa, ana.conta().getNumero(), null, null).lancamentos())
                .extracting(l -> l.getTipo())
                .containsExactly(TipoLancamento.ABERTURA, TipoLancamento.DEPOSITO, TipoLancamento.SAQUE);
    }

    @Test
    void pesquisaPorNomeCpfOuNumeroSoParaFuncionarios() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "100", "0");
        cenario.cliente("Joao Silva", "100", "0");
        UsuarioLogado caixa = cenario.funcionario(Perfil.CAIXA);

        assertThat(contas.pesquisar(caixa, "souza")).extracting(c -> c.getNumero()).containsExactly(ana.conta().getNumero());
        assertThat(contas.pesquisar(caixa, ana.cliente().getCpf().substring(0, 6))).hasSize(1);
        assertThat(contas.pesquisar(caixa, ana.conta().getNumero())).hasSize(1);
        assertThatThrownBy(() -> contas.pesquisar(ana.usuario(), "")).isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void extratoRecusaPeriodoInvalido() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "100", "0");
        String n = ana.conta().getNumero();
        assertThatThrownBy(() -> contas.extrato(ana.usuario(), n, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)))
                .hasMessage("A data inicial não pode ser depois da data final.");
        assertThatThrownBy(() -> contas.extrato(ana.usuario(), n, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 9, 1)))
                .hasMessage("O período do extrato pode ter no máximo 1 ano.");
    }
}
