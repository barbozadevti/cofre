// Início do app: sessão, rotas por perfil e a "casca" (menu lateral + barra inferior no celular).
import { api, prepararCsrf, quandoPerderSessao } from "./api.js";
import { $, $$, avisar, avisarErro, escapar, icone } from "./ui.js";
import { telaDeLogin, telaDeTrocaDeSenha, telaDePerfil } from "./login.js";
import * as cliente from "./cliente.js";
import * as agencia from "./agencia.js";
import * as gerencia from "./gerencia.js";

const ROTAS = {
  CLIENTE: [
    { id: "inicio", nome: "Início", icone: "inicio", tela: cliente.inicio },
    { id: "pix", nome: "Pix", icone: "pix", tela: cliente.pix },
    { id: "extrato", nome: "Extrato", icone: "extrato", tela: cliente.extrato },
    { id: "caixinhas", nome: "Caixinhas", icone: "caixinha", tela: cliente.caixinhas },
    { id: "cartao", nome: "Cartão", icone: "cartao", tela: cliente.cartao },
    { id: "perfil", nome: "Perfil", icone: "perfil", tela: telaDePerfil },
  ],
  CAIXA: [
    { id: "balcao", nome: "Balcão", icone: "balcao", tela: agencia.balcao },
    { id: "perfil", nome: "Perfil", icone: "perfil", tela: telaDePerfil },
  ],
  GERENTE: [
    { id: "painel", nome: "Painel", icone: "painel", tela: gerencia.painel },
    { id: "contas", nome: "Contas", icone: "contas", tela: gerencia.contas },
    { id: "nova-conta", nome: "Abertura", icone: "novo", tela: gerencia.novaConta },
    { id: "balcao", nome: "Balcão", icone: "balcao", tela: agencia.balcao },
    { id: "auditoria", nome: "Auditoria", icone: "auditoria", tela: gerencia.auditoria },
    { id: "perfil", nome: "Perfil", icone: "perfil", tela: telaDePerfil },
  ],
};

const estado = { sessao: null };

function lerPrivacidade() {
  try {
    return localStorage.getItem("cofre.privado") === "1";
  } catch {
    return false;
  }
}

function gravarPrivacidade(ativo) {
  try {
    localStorage.setItem("cofre.privado", ativo ? "1" : "0");
  } catch {
    // sem armazenamento: vale só nesta página
  }
}

export function alternarPrivacidade() {
  const ativo = !document.body.classList.contains("privado");
  document.body.classList.toggle("privado", ativo);
  gravarPrivacidade(ativo);
  $$("[data-privacidade]").forEach((b) => {
    b.innerHTML = icone(ativo ? "olhoFechado" : "olho");
    b.setAttribute("aria-label", ativo ? "Mostrar valores" : "Esconder valores");
  });
}

export function botaoPrivacidade() {
  const ativo = document.body.classList.contains("privado");
  return `<button type="button" class="icone-botao" data-privacidade aria-label="${ativo ? "Mostrar valores" : "Esconder valores"}">${icone(ativo ? "olhoFechado" : "olho")}</button>`;
}

export async function sair() {
  await api("/api/auth/sair", { metodo: "POST" }).catch(() => null);
  estado.sessao = null;
  location.hash = "";
  await prepararCsrf();
  telaDeLogin(entrou);
}

function rotas() {
  return ROTAS[estado.sessao.perfil] || [];
}

function desenharCasca() {
  const lista = rotas();
  const links = (classe) => lista.map((r) => `<a href="#/${r.id}" data-rota="${r.id}">${icone(r.icone)}<span>${r.nome}</span></a>`).join("");
  $("#app").innerHTML = `<div class="casca">
      <aside class="lateral">
        <div class="marca"><img src="favicon.svg" alt=""><div><strong>Cofre</strong><small>banco digital</small></div></div>
        <nav class="navegacao" aria-label="Menu">${links()}</nav>
        <div class="usuario-lateral">
          <span class="nome">${escapar(estado.sessao.nome)}</span>
          <span class="selo-perfil">${escapar(nomeDoPerfil(estado.sessao.perfil))}</span>
          <button type="button" class="botao fantasma pequeno" data-sair>${icone("sair")} Sair</button>
        </div>
      </aside>
      <main class="principal" id="principal" tabindex="-1"></main>
      <nav class="barra-inferior" aria-label="Menu">${links()}</nav>
    </div>`;
  $("[data-sair]").addEventListener("click", sair);
}

function nomeDoPerfil(perfil) {
  return { CLIENTE: "Cliente", CAIXA: "Caixa", GERENTE: "Gerente" }[perfil] || perfil;
}

async function navegar() {
  if (!estado.sessao) return;
  const lista = rotas();
  const id = location.hash.replace(/^#\/?/, "").split("?")[0];
  const rota = lista.find((r) => r.id === id) || lista[0];
  if (rota.id !== id) {
    history.replaceState(null, "", "#/" + rota.id);
  }
  $$("[data-rota]").forEach((a) => a.setAttribute("aria-current", a.dataset.rota === rota.id ? "page" : "false"));
  const principal = $("#principal");
  principal.innerHTML = `<div class="carregando"><span class="giro"></span></div>`;
  try {
    await rota.tela(principal, { sessao: estado.sessao, sair, recarregar: navegar });
  } catch (erro) {
    if (erro.status !== 401) {
      principal.innerHTML = `<div class="vazio">${icone("escudo")}<span>${escapar(erro.message)}</span></div>`;
      avisarErro(erro);
    }
  }
  $$("[data-privacidade]").forEach((b) => b.addEventListener("click", alternarPrivacidade));
  principal.focus({ preventScroll: true });
  window.scrollTo({ top: 0 });
}

async function entrou(sessao) {
  estado.sessao = sessao;
  if (sessao.trocarSenha) {
    telaDeTrocaDeSenha(sessao, async () => {
      estado.sessao = { ...sessao, trocarSenha: false };
      avisar("Senha criada. Bem-vindo ao Cofre!", { titulo: "Tudo certo" });
      desenharCasca();
      await navegar();
    }, sair);
    return;
  }
  desenharCasca();
  await navegar();
}

async function iniciar() {
  document.body.classList.toggle("privado", lerPrivacidade());
  quandoPerderSessao(() => {
    if (!estado.sessao) return;
    estado.sessao = null;
    avisar("Sua sessão expirou. Entre de novo.", { titulo: "Sessão encerrada", erro: true });
    telaDeLogin(entrou);
  });
  window.addEventListener("hashchange", navegar);
  await prepararCsrf();
  try {
    await entrou(await api("/api/auth/eu"));
  } catch {
    telaDeLogin(entrou);
  }
}

iniciar();
