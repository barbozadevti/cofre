package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.barboza.cofre.caixinha.CaixinhaService;
import dev.barboza.cofre.dominio.AcessoNegadoException;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.SituacaoConta;
import dev.barboza.cofre.pix.PixService;
import dev.barboza.cofre.pix.TipoChavePix;
import dev.barboza.cofre.seguranca.AutenticacaoService;
import dev.barboza.cofre.seguranca.CredenciaisInvalidasException;
import dev.barboza.cofre.seguranca.Perfil;
import dev.barboza.cofre.seguranca.UsuarioBloqueadoException;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.servico.GerenciaService;

@TesteIntegrado
class GerenciaEAcessoTest {

    @Autowired
    GerenciaService gerencia;

    @Autowired
    AutenticacaoService autenticacao;

    @Autowired
    PixService pix;

    @Autowired
    CaixinhaService caixinhas;

    @Autowired
    Cenario cenario;

    private static BigDecimal v(String valor) {
        return new BigDecimal(valor);
    }

    private GerenciaService.NovaConta nova(String cpf, String email) {
        return new GerenciaService.NovaConta("Rafael Barboza", cpf, email, "(11) 91234-5678", "0321", v("1500"), v("800"));
    }

    @Test
    void abreContaParaClienteNovoComSenhaProvisoria() {
        UsuarioLogado gerente = cenario.funcionario(Perfil.GERENTE);

        GerenciaService.ContaAberta aberta = gerencia.abrirConta(gerente, nova("52998224725", "rafael@teste.dev"));

        assertThat(aberta.senhaProvisoria()).hasSize(10).matches(".*\\d.*");
        assertThat(aberta.conta().getLimite()).isEqualTo(v("800.00"));
        assertThat(aberta.mensagem()).startsWith("Olá Rafael Barboza, obrigado por criar uma conta em nosso banco, sua agência é 0321");

        // O cliente entra pelo CPF com a senha provisória e é obrigado a trocar.
        AutenticacaoService.Resultado login = autenticacao.autenticar("529.982.247-25", aberta.senhaProvisoria());
        assertThat(login.trocarSenha()).isTrue();
        assertThatThrownBy(() -> autenticacao.trocarSenha(login.usuario(), aberta.senhaProvisoria(), "curta1"))
                .hasMessage("A senha precisa ter pelo menos 8 caracteres.");
        assertThatThrownBy(() -> autenticacao.trocarSenha(login.usuario(), aberta.senhaProvisoria(), "somenteletras"))
                .hasMessage("A senha precisa ter letras e números.");
        assertThatThrownBy(() -> autenticacao.trocarSenha(login.usuario(), aberta.senhaProvisoria(), "rafael2026x"))
                .hasMessage("A senha não pode conter o seu e-mail.");
        autenticacao.trocarSenha(login.usuario(), aberta.senhaProvisoria(), "MinhaSenha2026");
        assertThat(autenticacao.autenticar("rafael@teste.dev", "MinhaSenha2026").trocarSenha()).isFalse();
    }

    @Test
    void cpfExistenteGanhaContaAdicionalSemNovaSenha() {
        UsuarioLogado gerente = cenario.funcionario(Perfil.GERENTE);
        gerencia.abrirConta(gerente, nova("52998224725", "rafael@teste.dev"));

        GerenciaService.ContaAberta segunda = gerencia.abrirConta(gerente, nova("529.982.247-25", "outro@teste.dev"));

        assertThat(segunda.senhaProvisoria()).isNull();
    }

    @Test
    void soOGerenteMexeEmLimiteBloqueioEEncerramento() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "0", "0");
        UsuarioLogado caixa = cenario.funcionario(Perfil.CAIXA);
        String n = ana.conta().getNumero();

        assertThatThrownBy(() -> gerencia.definirLimite(caixa, n, v("100"))).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> gerencia.bloquear(ana.usuario(), n, "motivo qualquer")).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> gerencia.indicadores(caixa)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> gerencia.abrirConta(caixa, nova("52998224725", "x@teste.dev")))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void bloqueioImpedeSaidasDoCliente() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "500", "0");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "0", "0");
        cenario.chave(joao, TipoChavePix.EMAIL, "joao@teste.dev");
        UsuarioLogado gerente = cenario.funcionario(Perfil.GERENTE);

        gerencia.bloquear(gerente, ana.conta().getNumero(), "Suspeita de fraude no Pix");

        assertThatThrownBy(() -> pix.enviar(ana.usuario(), ana.conta().getNumero(), "joao@teste.dev", v("10"), null, null))
                .hasMessageContaining("está bloqueada para saídas");
        gerencia.desbloquear(gerente, ana.conta().getNumero());
        pix.enviar(ana.usuario(), ana.conta().getNumero(), "joao@teste.dev", v("10"), null, null);
    }

    @Test
    void encerramentoExigeCaixinhasZeradasERemoveChaves() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "100", "0");
        cenario.chave(ana, TipoChavePix.EMAIL, "ana@teste.dev");
        UsuarioLogado gerente = cenario.funcionario(Perfil.GERENTE);
        var reserva = caixinhas.criar(ana.usuario(), ana.conta().getNumero(), "Reserva", null);
        caixinhas.guardar(ana.usuario(), reserva.getId(), v("100"));

        assertThatThrownBy(() -> gerencia.encerrar(gerente, ana.conta().getNumero()))
                .hasMessage("O cliente ainda tem R$ 100,00 em caixinhas; é preciso resgatar antes de encerrar.");

        caixinhas.resgatar(ana.usuario(), reserva.getId(), v("100"));
        new ContaHelper(ana.conta()).zerar();
        Conta encerrada = gerencia.encerrar(gerente, ana.conta().getNumero());

        assertThat(encerrada.getSituacao()).isEqualTo(SituacaoConta.ENCERRADA);
        assertThat(pix.chaves(ana.usuario(), ana.conta().getNumero())).isEmpty();
    }

    @Test
    void cincoSenhasErradasBloqueiamPorQuinzeMinutos() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "0", "0");
        String login = ana.usuario().login();

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> autenticacao.autenticar(login, "errada")).isInstanceOf(CredenciaisInvalidasException.class);
        }
        assertThatThrownBy(() -> autenticacao.autenticar(login, "errada"))
                .isInstanceOf(UsuarioBloqueadoException.class)
                .hasMessage("Acesso bloqueado por excesso de tentativas. Tente de novo em 15 minutos.");
        // Nem a senha certa entra enquanto estiver bloqueado.
        assertThatThrownBy(() -> autenticacao.autenticar(login, Cenario.SENHA)).isInstanceOf(UsuarioBloqueadoException.class);
    }

    @Test
    void usuarioInexistenteRecebeAMesmaMensagem() {
        assertThatThrownBy(() -> autenticacao.autenticar("ninguem@teste.dev", "x"))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("Login ou senha incorretos.");
    }

    @Test
    void redefinirSenhaDesbloqueiaOAcesso() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "0", "0");
        UsuarioLogado gerente = cenario.funcionario(Perfil.GERENTE);
        for (int i = 0; i < 5; i++) {
            try {
                autenticacao.autenticar(ana.usuario().login(), "errada");
            } catch (RuntimeException e) {
                // esperado
            }
        }

        String nova = gerencia.redefinirSenha(gerente, ana.conta().getNumero());

        assertThat(autenticacao.autenticar(ana.usuario().login(), nova).trocarSenha()).isTrue();
    }

    @Test
    void indicadoresDaAgencia() {
        cenario.cliente("Ana Souza", "300", "1000");
        cenario.cliente("Joao Silva", "200", "500");
        UsuarioLogado gerente = cenario.funcionario(Perfil.GERENTE);

        GerenciaService.Indicadores i = gerencia.indicadores(gerente);

        assertThat(i.contasAtivas()).isEqualTo(2);
        assertThat(i.emCustodia()).isEqualByComparingTo("500.00");
        assertThat(i.limiteConcedido()).isEqualByComparingTo("1500.00");
    }

    /** Zera o saldo por um saque (a conta precisa estar zerada para encerrar). */
    private record ContaHelper(Conta conta) {
        void zerar() {
            if (conta.getSaldo().signum() > 0) {
                conta.debitar(dev.barboza.cofre.dominio.TipoLancamento.SAQUE, conta.getSaldo(), null, null, "Z", RelogioFixo.AGORA, false);
            }
        }
    }
}
