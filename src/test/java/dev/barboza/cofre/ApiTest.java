package dev.barboza.cofre;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RelogioFixo.class)
@Transactional
class ApiTest {

    @Autowired
    MockMvc mvc;

    private ResultActions postar(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void abrir(String titular, String saldo) throws Exception {
        postar("/api/contas", "{\"titular\":\"" + titular + "\",\"saldoInicial\":" + saldo + "}")
                .andExpect(status().isCreated());
    }

    @Test
    void abreContaComMensagemDoDesafio() throws Exception {
        postar("/api/contas", """
                {"titular": "Mario Andrade", "agencia": "0678", "saldoInicial": 237.48}
                """)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/contas/10001-3"))
                .andExpect(jsonPath("$.conta.numero").value("10001-3"))
                .andExpect(jsonPath("$.conta.agencia").value("0678"))
                .andExpect(jsonPath("$.conta.saldo").value(237.48))
                .andExpect(jsonPath("$.conta.situacao").value("ATIVA"))
                .andExpect(jsonPath("$.mensagem").value(
                        "Olá Mario Andrade, obrigado por criar uma conta em nosso banco, sua agência é 0678, "
                                + "conta 10001-3 e seu saldo R$ 237,48 já está disponível para saque."));
    }

    @Test
    void listaEBuscaContas() throws Exception {
        abrir("Mario Andrade", "10");
        abrir("Ana Souza", "20");

        mvc.perform(get("/api/contas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].titular").value("Ana Souza"));
        mvc.perform(get("/api/contas/100021"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("10002-1"));
    }

    @Test
    void depositaESaca() throws Exception {
        abrir("Mario Andrade", "100");

        postar("/api/contas/10001-3/depositos", "{\"valor\": 50.5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(150.5));
        postar("/api/contas/10001-3/saques", "{\"valor\": 0.5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(150.0));
    }

    @Test
    void saqueSemSaldoDevolve422EmProblemJson() throws Exception {
        abrir("Mario Andrade", "80");

        postar("/api/contas/10001-3/saques", "{\"valor\": 100}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Operação não permitida"))
                .andExpect(jsonPath("$.detail").value("Saldo insuficiente: disponível R$ 80,00, solicitado R$ 100,00."));
    }

    @Test
    void contaInexistenteDevolve404() throws Exception {
        mvc.perform(get("/api/contas/99999-7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Conta 99999-7 não encontrada."));
    }

    @Test
    void digitoErradoDevolve422() throws Exception {
        mvc.perform(get("/api/contas/10001-5"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("dígito verificador")));
    }

    @Test
    void camposObrigatoriosDevolvem400() throws Exception {
        postar("/api/contas", "{\"titular\": \" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.detail").value(containsString("Informe o nome do titular.")))
                .andExpect(jsonPath("$.detail").value(containsString("Informe o saldo inicial (pode ser 0).")));
    }

    @Test
    void jsonMalFormadoDevolve400() throws Exception {
        postar("/api/contas/10001-3/depositos", "{\"valor\": \"dez\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Não foi possível ler a requisição")));
    }

    @Test
    void transfere() throws Exception {
        abrir("Ana Souza", "500");
        abrir("Joao Silva", "0");

        postar("/api/transferencias", """
                {"origem": "10001-3", "destino": "10002-1", "valor": 200}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origem.saldo").value(300.0))
                .andExpect(jsonPath("$.destino.saldo").value(200.0));
    }

    @Test
    void extratoComTotais() throws Exception {
        abrir("Mario Andrade", "100");
        postar("/api/contas/10001-3/saques", "{\"valor\": 40}");

        mvc.perform(get("/api/contas/10001-3/extrato").param("de", "2026-09-01").param("ate", "2026-09-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.de").value("2026-09-01"))
                .andExpect(jsonPath("$.saldoInicial").value(0))
                .andExpect(jsonPath("$.entradas").value(100.0))
                .andExpect(jsonPath("$.saidas").value(40.0))
                .andExpect(jsonPath("$.saldoFinal").value(60.0))
                .andExpect(jsonPath("$.lancamentos", hasSize(2)))
                .andExpect(jsonPath("$.lancamentos[1].descricao").value("Saque"))
                .andExpect(jsonPath("$.lancamentos[1].valor").value(-40.0))
                .andExpect(jsonPath("$.lancamentos[1].dataHora").value("2026-09-26T15:00:00Z"));
    }

    @Test
    void extratoComDataInvalidaDevolve400() throws Exception {
        abrir("Mario Andrade", "100");
        mvc.perform(get("/api/contas/10001-3/extrato").param("de", "26/09/2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void extratoEmCsvParaExcel() throws Exception {
        abrir("Mario Andrade", "100");
        postar("/api/contas/10001-3/saques", "{\"valor\": 40.5}");

        byte[] corpo = mvc.perform(get("/api/contas/10001-3/extrato.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"extrato-10001-3-2026-08-28-a-2026-09-26.csv\""))
                .andReturn().getResponse().getContentAsByteArray();

        String csv = new String(corpo, StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(csv).isEqualTo("﻿"
                + "Data;Descrição;Valor;Saldo\r\n"
                + ";Saldo anterior;;0,00\r\n"
                + "26/09/2026 12:00;Abertura de conta;100,00;100,00\r\n"
                + "26/09/2026 12:00;Saque;-40,50;59,50\r\n");
    }

    @Test
    void encerraContaZerada() throws Exception {
        abrir("Mario Andrade", "0");

        mvc.perform(delete("/api/contas/10001-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("ENCERRADA"));
        postar("/api/contas/10001-3/depositos", "{\"valor\": 1}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("A conta 10001-3 está encerrada."));
    }

    @Test
    void siteESaudeRespondem() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
