package dev.barboza.cofre.terminal;

import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.function.Supplier;

import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.ContaNaoEncontradaException;
import dev.barboza.cofre.dominio.Dinheiro;
import dev.barboza.cofre.dominio.Lancamento;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import dev.barboza.cofre.servico.ContaService;
import dev.barboza.cofre.servico.Extrato;

/**
 * Menu do Cofre no terminal. É a evolução do desafio "conta bancária pelo terminal":
 * as mesmas perguntas na abertura, agora sobre o mesmo núcleo usado pelo site e pela API.
 */
public class TerminalBancario {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ContaService.FUSO);

    private final ContaService servico;
    private final Scanner entrada;
    private final PrintStream saida;

    public TerminalBancario(ContaService servico, InputStream entrada, PrintStream saida) {
        this.servico = servico;
        this.entrada = new Scanner(entrada);
        this.saida = saida;
    }

    public void executar() {
        saida.println();
        saida.println("=== COFRE | terminal da agência ===");
        while (true) {
            saida.println();
            saida.println("1) Abrir conta      2) Depositar     3) Sacar");
            saida.println("4) Transferir       5) Extrato       6) Listar contas");
            saida.println("0) Sair");
            String opcao = perguntar("Escolha uma opção:");
            if (opcao == null || opcao.equals("0")) {
                saida.println("Até logo!");
                return;
            }
            try {
                switch (opcao) {
                    case "1" -> abrirConta();
                    case "2" -> depositar();
                    case "3" -> sacar();
                    case "4" -> transferir();
                    case "5" -> extrato();
                    case "6" -> listar();
                    default -> saida.println("Opção inválida. Digite um número de 0 a 6.");
                }
            } catch (FimDaEntrada e) {
                saida.println("Até logo!");
                return;
            } catch (OperacaoInvalidaException | ContaNaoEncontradaException e) {
                saida.println("Não foi possível: " + e.getMessage());
            }
        }
    }

    private void abrirConta() {
        String agencia = obrigatorio("Por favor, digite o número da Agência ! (Enter para 0001)", true);
        String titular = obrigatorio("Por favor, digite o nome do Cliente !", false);
        BigDecimal saldo = repetir(() -> Dinheiro.validarValorOuZero(
                Dinheiro.interpretar(obrigatorio("Por favor, digite o saldo !", false))));
        Conta conta = servico.abrir(titular, agencia.isEmpty() ? null : agencia, saldo);
        saida.println(conta.mensagemDeBoasVindas());
    }

    private void depositar() {
        String numero = obrigatorio("Número da conta:", false);
        BigDecimal valor = lerValor("Valor do depósito:");
        Conta conta = servico.depositar(numero, valor);
        saida.println("Depósito feito. Novo saldo da conta " + conta.getNumero() + ": " + Dinheiro.formatar(conta.getSaldo()));
    }

    private void sacar() {
        String numero = obrigatorio("Número da conta:", false);
        BigDecimal valor = lerValor("Valor do saque:");
        Conta conta = servico.sacar(numero, valor);
        saida.println("Saque feito. Novo saldo da conta " + conta.getNumero() + ": " + Dinheiro.formatar(conta.getSaldo()));
    }

    private void transferir() {
        String origem = obrigatorio("Conta de origem:", false);
        String destino = obrigatorio("Conta de destino:", false);
        BigDecimal valor = lerValor("Valor da transferência:");
        ContaService.Transferencia t = servico.transferir(origem, destino, valor);
        saida.println("Transferência feita.");
        saida.println("  " + t.origem().getNumero() + " (" + t.origem().getTitular() + "): " + Dinheiro.formatar(t.origem().getSaldo()));
        saida.println("  " + t.destino().getNumero() + " (" + t.destino().getTitular() + "): " + Dinheiro.formatar(t.destino().getSaldo()));
    }

    private void extrato() {
        String numero = obrigatorio("Número da conta:", false);
        Extrato extrato = servico.extrato(numero, null, null);
        Conta conta = extrato.conta();
        saida.println();
        saida.println("Extrato da conta " + conta.getNumero() + " | ag. " + conta.getAgencia() + " | " + conta.getTitular());
        saida.println("Período: últimos 30 dias");
        saida.printf("%-16s  %-32s  %14s  %14s%n", "Data", "Descrição", "Valor", "Saldo");
        saida.printf("%-16s  %-32s  %14s  %14s%n", "", "Saldo anterior", "", Dinheiro.formatar(extrato.saldoInicial()));
        for (Lancamento l : extrato.lancamentos()) {
            saida.printf("%-16s  %-32s  %14s  %14s%n", DATA_HORA.format(l.getDataHora()), l.descricao(),
                    Dinheiro.formatar(l.valorComSinal()), Dinheiro.formatar(l.getSaldoApos()));
        }
        saida.println("Entradas: " + Dinheiro.formatar(extrato.entradas()) + "   Saídas: "
                + Dinheiro.formatar(extrato.saidas()) + "   Saldo atual: " + Dinheiro.formatar(extrato.saldoFinal()));
    }

    private void listar() {
        List<Conta> contas = servico.listar();
        if (contas.isEmpty()) {
            saida.println("Nenhuma conta aberta ainda.");
            return;
        }
        saida.printf("%-9s  %-7s  %-30s  %14s  %s%n", "Conta", "Agência", "Titular", "Saldo", "Situação");
        for (Conta c : contas) {
            saida.printf("%-9s  %-7s  %-30s  %14s  %s%n", c.getNumero(), c.getAgencia(), c.getTitular(),
                    Dinheiro.formatar(c.getSaldo()), c.getSituacao() == dev.barboza.cofre.dominio.SituacaoConta.ATIVA ? "ativa" : "encerrada");
        }
    }

    private BigDecimal lerValor(String pergunta) {
        return repetir(() -> Dinheiro.validarValor(Dinheiro.interpretar(obrigatorio(pergunta, false))));
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

    private String obrigatorio(String pergunta, boolean podeFicarVazio) {
        while (true) {
            String resposta = perguntar(pergunta);
            if (resposta == null) {
                throw new FimDaEntrada();
            }
            if (podeFicarVazio || !resposta.isEmpty()) {
                return resposta;
            }
            saida.println("Este campo não pode ficar vazio.");
        }
    }

    private String perguntar(String pergunta) {
        saida.println(pergunta);
        // Remove o BOM que alguns terminais (ex.: PowerShell em UTF-8) mandam no início da entrada.
        return entrada.hasNextLine() ? entrada.nextLine().replace("﻿", "").trim() : null;
    }

    /** A entrada acabou (Ctrl+Z / Ctrl+D ou fim do arquivo): encerra sem erro. */
    private static final class FimDaEntrada extends RuntimeException {
    }
}
