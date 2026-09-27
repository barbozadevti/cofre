package dev.barboza.cofre.terminal;

import java.io.Console;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.function.Supplier;

import dev.barboza.cofre.dominio.AcessoNegadoException;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.Dinheiro;
import dev.barboza.cofre.dominio.Lancamento;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import dev.barboza.cofre.dominio.RecursoNaoEncontradoException;
import dev.barboza.cofre.seguranca.AutenticacaoService;
import dev.barboza.cofre.seguranca.CredenciaisInvalidasException;
import dev.barboza.cofre.seguranca.Perfil;
import dev.barboza.cofre.seguranca.UsuarioBloqueadoException;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.servico.ContaService;
import dev.barboza.cofre.servico.Extrato;
import dev.barboza.cofre.servico.GerenciaService;

/**
 * Terminal da agência (caixa e gerente). É a evolução do desafio "conta bancária pelo terminal":
 * as mesmas perguntas na abertura de conta, agora com login e sobre o mesmo núcleo do site e da API.
 */
public class TerminalBancario {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ContaService.FUSO);

    private final ContaService contas;
    private final GerenciaService gerencia;
    private final AutenticacaoService autenticacao;
    private final Scanner entrada;
    private final PrintStream saida;
    private final Console console;
    private UsuarioLogado operador;

    public TerminalBancario(ContaService contas, GerenciaService gerencia, AutenticacaoService autenticacao,
            InputStream entrada, PrintStream saida, Console console) {
        this.contas = contas;
        this.gerencia = gerencia;
        this.autenticacao = autenticacao;
        this.entrada = new Scanner(entrada);
        this.saida = saida;
        this.console = console;
    }

    public void executar() {
        saida.println();
        saida.println("=== COFRE | terminal da agência ===");
        try {
            if (!entrar()) {
                return;
            }
            menu();
        } catch (FimDaEntrada e) {
            saida.println("Até logo!");
        }
    }

    private boolean entrar() {
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            String login = obrigatorio("Login (e-mail):");
            String senha = senha("Senha:");
            try {
                UsuarioLogado u = autenticacao.autenticar(login, senha).usuario();
                if (!u.perfil().funcionario()) {
                    saida.println("O terminal da agência é só para caixa e gerente. Clientes usam o app.");
                    continue;
                }
                operador = u;
                saida.println("Olá, " + u.nome() + " (" + u.perfil().nome() + ").");
                return true;
            } catch (CredenciaisInvalidasException | UsuarioBloqueadoException e) {
                saida.println(e.getMessage());
            }
        }
        saida.println("Muitas tentativas. Até logo!");
        return false;
    }

    private void menu() {
        while (true) {
            saida.println();
            saida.println("1) Buscar contas     2) Depósito em espécie   3) Saque em espécie");
            saida.println("4) Extrato           5) Abrir conta (gerente) 0) Sair");
            String opcao = perguntar("Escolha uma opção:");
            if (opcao == null || opcao.equals("0")) {
                saida.println("Até logo!");
                return;
            }
            try {
                switch (opcao) {
                    case "1" -> buscar();
                    case "2" -> depositar();
                    case "3" -> sacar();
                    case "4" -> extrato();
                    case "5" -> abrirConta();
                    default -> saida.println("Opção inválida. Digite um número de 0 a 5.");
                }
            } catch (OperacaoInvalidaException | RecursoNaoEncontradoException | AcessoNegadoException e) {
                saida.println("Não foi possível: " + e.getMessage());
            }
        }
    }

    /** Abertura com as perguntas do desafio original, mais CPF e e-mail (o cliente ganha acesso ao app). */
    private void abrirConta() {
        if (operador.perfil() != Perfil.GERENTE) {
            throw new AcessoNegadoException("Operação disponível só para o gerente.");
        }
        String nome = obrigatorio("Por favor, digite o nome do Cliente !");
        String cpf = obrigatorio("Por favor, digite o CPF do Cliente !");
        String email = obrigatorio("Por favor, digite o e-mail do Cliente !");
        String agencia = perguntarOuFim("Por favor, digite o número da Agência ! (Enter para 0001)");
        BigDecimal saldo = repetir(() -> Dinheiro.validarValorOuZero(Dinheiro.interpretar(obrigatorio("Por favor, digite o saldo !"))));
        GerenciaService.ContaAberta aberta = gerencia.abrirConta(operador, new GerenciaService.NovaConta(nome, cpf, email,
                null, agencia.isEmpty() ? null : agencia, saldo, null));
        saida.println(aberta.mensagem());
        if (aberta.senhaProvisoria() != null) {
            saida.println("Senha provisória do app: " + aberta.senhaProvisoria() + " (o cliente troca no primeiro acesso)");
        }
    }

    private void buscar() {
        List<Conta> encontradas = contas.pesquisar(operador, perguntarOuFim("Nome, CPF ou número (Enter para todas):"));
        if (encontradas.isEmpty()) {
            saida.println("Nenhuma conta encontrada.");
            return;
        }
        saida.printf("%-9s  %-7s  %-26s  %14s  %12s  %s%n", "Conta", "Agência", "Titular", "Saldo", "Limite", "Situação");
        for (Conta c : encontradas) {
            saida.printf("%-9s  %-7s  %-26s  %14s  %12s  %s%n", c.getNumero(), c.getAgencia(), c.getCliente().getNome(),
                    Dinheiro.formatar(c.getSaldo()), Dinheiro.formatar(c.getLimite()), c.getSituacao().name().toLowerCase());
        }
    }

    private void depositar() {
        String numero = obrigatorio("Número da conta:");
        BigDecimal valor = lerValor("Valor do depósito:");
        Conta conta = contas.depositarEmEspecie(operador, numero, valor);
        saida.println("Depósito feito. Novo saldo da conta " + conta.getNumero() + ": " + Dinheiro.formatar(conta.getSaldo()));
    }

    private void sacar() {
        String numero = obrigatorio("Número da conta:");
        BigDecimal valor = lerValor("Valor do saque:");
        Conta conta = contas.sacarEmEspecie(operador, numero, valor);
        saida.println("Saque feito. Novo saldo da conta " + conta.getNumero() + ": " + Dinheiro.formatar(conta.getSaldo()));
    }

    private void extrato() {
        Extrato extrato = contas.extrato(operador, obrigatorio("Número da conta:"), null, null);
        Conta conta = extrato.conta();
        saida.println();
        saida.println("Extrato da conta " + conta.getNumero() + " | ag. " + conta.getAgencia() + " | " + conta.getCliente().getNome());
        saida.println("Período: últimos 30 dias");
        saida.printf("%-16s  %-36s  %14s  %14s%n", "Data", "Descrição", "Valor", "Saldo");
        saida.printf("%-16s  %-36s  %14s  %14s%n", "", "Saldo anterior", "", Dinheiro.formatar(extrato.saldoInicial()));
        for (Lancamento l : extrato.lancamentos()) {
            saida.printf("%-16s  %-36s  %14s  %14s%n", DATA_HORA.format(l.getDataHora()), cortar(l.descricao(), 36),
                    Dinheiro.formatar(l.valorComSinal()), Dinheiro.formatar(l.getSaldoApos()));
        }
        saida.println("Entradas: " + Dinheiro.formatar(extrato.entradas()) + "   Saídas: "
                + Dinheiro.formatar(extrato.saidas()) + "   Saldo atual: " + Dinheiro.formatar(extrato.saldoFinal()));
    }

    private BigDecimal lerValor(String pergunta) {
        return repetir(() -> Dinheiro.validarValor(Dinheiro.interpretar(obrigatorio(pergunta))));
    }

    /** Repete a pergunta enquanto o valor digitado for inválido, explicando o motivo. */
    private <T> T repetir(Supplier<T> leitura) {
        while (true) {
            try {
                return leitura.get();
            } catch (OperacaoInvalidaException e) {
                saida.println(e.getMessage());
            }
        }
    }

    private String obrigatorio(String pergunta) {
        while (true) {
            String resposta = perguntarOuFim(pergunta);
            if (!resposta.isEmpty()) {
                return resposta;
            }
            saida.println("Este campo não pode ficar vazio.");
        }
    }

    private String perguntarOuFim(String pergunta) {
        String resposta = perguntar(pergunta);
        if (resposta == null) {
            throw new FimDaEntrada();
        }
        return resposta;
    }

    /** No console de verdade a senha não aparece na tela; com entrada redirecionada, lê a linha. */
    private String senha(String pergunta) {
        if (console != null) {
            char[] lida = console.readPassword(pergunta + " ");
            if (lida == null) {
                throw new FimDaEntrada();
            }
            return new String(lida);
        }
        return perguntarOuFim(pergunta);
    }

    private String perguntar(String pergunta) {
        saida.println(pergunta);
        // Remove o BOM que alguns terminais (ex.: PowerShell em UTF-8) mandam no início da entrada.
        return entrada.hasNextLine() ? entrada.nextLine().replace("﻿", "").trim() : null;
    }

    private static String cortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }

    /** A entrada acabou (Ctrl+Z / Ctrl+D ou fim do arquivo): encerra sem erro. */
    private static final class FimDaEntrada extends RuntimeException {
    }
}
