package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.servico.ContaService;
import dev.barboza.cofre.terminal.TerminalBancario;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(RelogioFixo.class)
@Transactional
class TerminalTest {

    @Autowired
    ContaService servico;

    private String executar(String... linhas) {
        String entrada = String.join("\n", linhas) + "\n";
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        new TerminalBancario(servico, new ByteArrayInputStream(entrada.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(saida, true, StandardCharsets.UTF_8)).executar();
        return saida.toString(StandardCharsets.UTF_8);
    }

    @Test
    void abreContaComAsPerguntasDoDesafio() {
        String saida = executar("1", "0678", "Mario Andrade", "237,48", "0");

        assertThat(saida)
                .contains("Por favor, digite o número da Agência !")
                .contains("Por favor, digite o nome do Cliente !")
                .contains("Por favor, digite o saldo !")
                .contains("Olá Mario Andrade, obrigado por criar uma conta em nosso banco, sua agência é 0678, "
                        + "conta 10001-3 e seu saldo R$ 237,48 já está disponível para saque.")
                .endsWith("Até logo!" + System.lineSeparator());
    }

    @Test
    void pedeDeNovoQuandoOValorEInvalido() {
        String saida = executar("1", "", "Ana Souza", "abc", "-5", "1.234,56", "0");

        assertThat(saida)
                .contains("Valor inválido: use números, por exemplo 150,75.")
                .contains("O valor não pode ser negativo.")
                .contains("sua agência é 0001, conta 10001-3 e seu saldo R$ 1.234,56");
    }

    @Test
    void depositaSacaTransfereEMostraExtrato() {
        servico.abrir("Ana Souza", null, new java.math.BigDecimal("500"));
        servico.abrir("Joao Silva", null, new java.math.BigDecimal("0"));

        String saida = executar(
                "2", "10001-3", "100",
                "3", "100013", "50,25",
                "4", "10001-3", "10002-1", "200",
                "5", "10002-1",
                "6",
                "0");

        assertThat(saida)
                .contains("Depósito feito. Novo saldo da conta 10001-3: R$ 600,00")
                .contains("Saque feito. Novo saldo da conta 10001-3: R$ 549,75")
                .contains("10001-3 (Ana Souza): R$ 349,75")
                .contains("10002-1 (Joao Silva): R$ 200,00")
                .contains("Transferência de 10001-3")
                .contains("Entradas: R$ 200,00   Saídas: R$ 0,00   Saldo atual: R$ 200,00")
                .contains("Joao Silva");
    }

    @Test
    void explicaErrosSemEncerrar() {
        servico.abrir("Ana Souza", null, new java.math.BigDecimal("10"));

        String saida = executar("9", "3", "10001-3", "50", "2", "99999-9", "1", "0");

        assertThat(saida)
                .contains("Opção inválida. Digite um número de 0 a 6.")
                .contains("Não foi possível: Saldo insuficiente: disponível R$ 10,00, solicitado R$ 50,00.")
                .contains("Não foi possível: Número de conta inválido")
                .endsWith("Até logo!" + System.lineSeparator());
    }

    @Test
    void ignoraBomNoInicioDaEntrada() {
        assertThat(executar("﻿6", "0")).contains("Nenhuma conta aberta ainda.").doesNotContain("Opção inválida");
    }

    @Test
    void fimDaEntradaEncerraSemErro() {
        assertThat(executar("1", "0001")).endsWith("Até logo!" + System.lineSeparator());
        assertThat(executar("6")).contains("Nenhuma conta aberta ainda.");
    }
}
