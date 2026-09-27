package dev.barboza.cofre;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.pix.TipoChavePix;
import dev.barboza.cofre.seguranca.Perfil;
import dev.barboza.cofre.seguranca.UsuarioLogado;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({RelogioFixo.class, Cenario.class})
@Transactional
class ApiSegurancaTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Cenario cenario;

    private static RequestPostProcessor como(UsuarioLogado u) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(u, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.perfil().name()))));
    }

    @Test
    void semLoginRecebe401EmProblemJson() throws Exception {
        mvc.perform(get("/api/app/painel"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Faça login para continuar."));
    }

    @Test
    void siteDocumentacaoESaudeSaoPublicos() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("X-Frame-Options", "DENY"));
        mvc.perform(get("/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.info.title", startsWith("Cofre")));
    }

    @Test
    void loginRealCriaSessaoQueDaAcessoAoApp() throws Exception {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "100", "0");
        MockHttpSession sessao = new MockHttpSession();

        mvc.perform(post("/api/auth/entrar").session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + ana.cliente().getCpf() + "\",\"senha\":\"" + Cenario.SENHA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("CLIENTE"))
                .andExpect(jsonPath("$.trocarSenha").value(false));

        mvc.perform(get("/api/app/painel").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.contas", hasSize(1)))
                .andExpect(jsonPath("$.meses", hasSize(6)))
                .andExpect(header().string("Cache-Control", "no-store"));

        mvc.perform(post("/api/auth/sair").session(sessao).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void senhaErradaDevolve401ComMensagemGenerica() throws Exception {
        mvc.perform(post("/api/auth/entrar").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"ninguem@teste.dev\",\"senha\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Login ou senha incorretos."));
    }

    @Test
    void postSemTokenCsrfEhRecusado() throws Exception {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "100", "0");
        mvc.perform(post("/api/caixinhas").with(como(ana.usuario())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conta\":\"" + ana.conta().getNumero() + "\",\"nome\":\"Viagem\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cadaPerfilSoAcessaSuaArea() throws Exception {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "100", "0");
        UsuarioLogado caixa = cenario.funcionario(Perfil.CAIXA);
        UsuarioLogado gerente = cenario.funcionario(Perfil.GERENTE);

        mvc.perform(get("/api/gerencia/indicadores").with(como(ana.usuario()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/agencia/contas").with(como(ana.usuario()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/gerencia/indicadores").with(como(caixa))).andExpect(status().isForbidden());
        mvc.perform(get("/api/app/painel").with(como(caixa))).andExpect(status().isForbidden());
        mvc.perform(get("/api/agencia/contas").with(como(caixa))).andExpect(status().isOk());
        mvc.perform(get("/api/agencia/contas").with(como(gerente))).andExpect(status().isOk());
        mvc.perform(get("/api/gerencia/indicadores").with(como(gerente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contasAtivas").value(1));
    }

    @Test
    void contaDeOutroClienteDevolve404() throws Exception {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "100", "0");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "100", "0");

        mvc.perform(get("/api/app/contas/" + joao.conta().getNumero() + "/extrato").with(como(ana.usuario())))
                .andExpect(status().isNotFound());
    }

    @Test
    void pixPelaApiComIdempotencyKey() throws Exception {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "500", "0");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "0", "0");
        cenario.chave(joao, TipoChavePix.EMAIL, "joao@teste.dev");
        String corpo = "{\"conta\":\"" + ana.conta().getNumero() + "\",\"chave\":\"joao@teste.dev\",\"valor\":120.50}";

        String id = mvc.perform(post("/api/pix/envios").with(como(ana.usuario())).with(csrf())
                        .header("Idempotency-Key", "abc-1").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("PIX"))
                .andExpect(jsonPath("$.destino.nome").value("Joao Silva"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8).replaceAll(".*\"idTransacao\":\"([^\"]+)\".*", "$1");

        mvc.perform(post("/api/pix/envios").with(como(ana.usuario())).with(csrf())
                        .header("Idempotency-Key", "abc-1").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idTransacao").value(id));

        mvc.perform(get("/api/app/painel").with(como(ana.usuario())))
                .andExpect(jsonPath("$.saldoTotal").value(379.5));
    }

    @Test
    void erroDeRegraVira422EDadosInvalidos400() throws Exception {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "80", "0");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "0", "0");

        mvc.perform(post("/api/app/transferencias").with(como(ana.usuario())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origem\":\"" + ana.conta().getNumero() + "\",\"destino\":\"" + joao.conta().getNumero()
                                + "\",\"valor\":100}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Saldo insuficiente: disponível R$ 80,00, solicitado R$ 100,00."));

        mvc.perform(post("/api/app/transferencias").with(como(ana.usuario())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origem\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Informe a conta de origem.")));
    }

    @Test
    void senhaProvisoriaTravaTudoAteATroca() throws Exception {
        UsuarioLogado gerente = cenario.funcionario(Perfil.GERENTE);
        String resposta = mvc.perform(post("/api/gerencia/contas").with(como(gerente)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Rafael Barboza\",\"cpf\":\"52998224725\",\"email\":\"rafael@teste.dev\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensagem", startsWith("Olá Rafael Barboza, obrigado por criar uma conta")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String provisoria = resposta.replaceAll(".*\"senhaProvisoria\":\"([^\"]+)\".*", "$1");
        MockHttpSession sessao = new MockHttpSession();

        mvc.perform(post("/api/auth/entrar").session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"rafael@teste.dev\",\"senha\":\"" + provisoria + "\"}"))
                .andExpect(jsonPath("$.trocarSenha").value(true));
        mvc.perform(get("/api/app/painel").session(sessao))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Troque a senha"));

        mvc.perform(post("/api/auth/senha").session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"atual\":\"" + provisoria + "\",\"nova\":\"NovaSenha2026\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/app/painel").session(sessao)).andExpect(status().isOk());
    }

    @Test
    void gerenteAjustaLimitePelaApi() throws Exception {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "0", "0");
        UsuarioLogado gerente = cenario.funcionario(Perfil.GERENTE);

        mvc.perform(put("/api/gerencia/contas/" + ana.conta().getNumero() + "/limite").with(como(gerente)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"limite\":1500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limite").value(1500.0))
                .andExpect(jsonPath("$.disponivel").value(1500.0));
        mvc.perform(get("/api/gerencia/auditoria").with(como(gerente)))
                .andExpect(jsonPath("$.eventos[0].acao").value("LIMITE_ALTERADO"));
    }

    @Test
    void extratoEmCsvParaExcel() throws Exception {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "100", "0");

        byte[] corpo = mvc.perform(get("/api/app/contas/" + ana.conta().getNumero() + "/extrato.csv").with(como(ana.usuario())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        org.assertj.core.api.Assertions.assertThat(new String(corpo, StandardCharsets.UTF_8)).isEqualTo("﻿"
                + "Data;Descrição;Valor;Saldo\r\n"
                + ";Saldo anterior;;0,00\r\n"
                + "26/09/2026 12:00;Abertura de conta;100,00;100,00\r\n");
    }
}
