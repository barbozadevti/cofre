package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.barboza.cofre.seguranca.AutenticacaoService;
import dev.barboza.cofre.seguranca.Perfil;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.servico.ContaService;
import dev.barboza.cofre.servico.GerenciaService;
import dev.barboza.cofre.terminal.TerminalBancario;

@TesteIntegrado
class TerminalTest {

    @Autowired
    ContaService contas;

    @Autowired
    GerenciaService gerencia;

    @Autowired
    AutenticacaoService autenticacao;

    @Autowired
    Cenario cenario;

    private String executar(String... linhas) {
        String entrada = String.join("\n", linhas) + "\n";
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        new TerminalBancario(contas, gerencia, autenticacao, new ByteArrayInputStream(entrada.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(saida, true, StandardCharsets.UTF_8), null).executar();
        return saida.toString(StandardCharsets.UTF_8);
    }

    @Test
    void gerenteAbreContaComAsPerguntasDoDesafio() {
        UsuarioLogado gerente = cenario.funcionario(Perfil.GERENTE);

        String saida = executar(gerente.login(), Cenario.SENHA,
                "5", "Mario Andrade", "529.982.247-25", "mario@teste.dev", "0678", "abc", "237,48", "0");

        assertThat(saida)
                .contains("Olá, Gerente de Teste (Gerente).")
                .contains("Por favor, digite o nome do Cliente !")
                .contains("Por favor, digite o número da Agência !")
                .contains("Valor inválido: use números, por exemplo 150,75.")
                .contains("Olá Mario Andrade, obrigado por criar uma conta em nosso banco, sua agência é 0678, conta ")
                .contains("e seu saldo R$ 237,48 já está disponível para saque.")
                .contains("Senha provisória do app: ")
                .endsWith("Até logo!" + System.lineSeparator());
    }

    @Test
    void caixaDepositaSacaEVeExtratoMasNaoAbreConta() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "100", "500");
        UsuarioLogado caixa = cenario.funcionario(Perfil.CAIXA);
        String n = ana.conta().getNumero();

        String saida = executar(caixa.login(), Cenario.SENHA,
                "2", n, "1.000,00",
                "3", n.replace("-", ""), "1500",
                "4", n,
                "1", "souza",
                "5",
                "0");

        assertThat(saida)
                .contains("Depósito feito. Novo saldo da conta " + n + ": R$ 1.100,00")
                .contains("Saque feito. Novo saldo da conta " + n + ": -R$ 400,00")
                .contains("Depósito em espécie")
                .contains("Ana Souza")
                .contains("Não foi possível: Operação disponível só para o gerente.");
    }

    @Test
    void clienteNaoUsaOTerminalESenhaErradaEhExplicada() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "100", "0");

        String saida = executar(ana.usuario().login(), Cenario.SENHA, "caixa@x.dev", "errada", "outro@x.dev", "errada");

        assertThat(saida)
                .contains("O terminal da agência é só para caixa e gerente. Clientes usam o app.")
                .contains("Login ou senha incorretos.")
                .contains("Muitas tentativas. Até logo!");
    }

    @Test
    void fimDaEntradaEncerraSemErro() {
        assertThat(executar("alguem")).endsWith("Até logo!" + System.lineSeparator());
    }
}
