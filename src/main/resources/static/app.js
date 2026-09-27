"use strict";

const FUSO = "America/Sao_Paulo";
const moeda = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const dataHora = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short", timeZone: FUSO });

const $ = (id) => document.getElementById(id);

const estado = {
  contas: [],
  selecionada: null,
  aba: "deposito",
};

/* ---------- API ---------- */

async function api(caminho, opcoes = {}) {
  const resposta = await fetch(caminho, {
    ...opcoes,
    headers: { "Content-Type": "application/json", Accept: "application/json", ...(opcoes.headers || {}) },
  });
  const corpo = resposta.status === 204 ? null : await resposta.json().catch(() => null);
  if (!resposta.ok) {
    const erro = new Error(corpo?.detail || "Não foi possível concluir a operação.");
    erro.titulo = corpo?.title || "Erro";
    throw erro;
  }
  return corpo;
}

/* ---------- Formatação ---------- */

function formatarMoeda(valor) {
  return moeda.format(Number(valor)).replace(/ /g, " ");
}

/** Lê "1.234,56", "1234,56" ou "1234.56" e devolve o número (ou NaN). */
function lerValor(texto) {
  let limpo = String(texto || "").trim().replace(/R\$|\s/g, "");
  if (limpo.includes(",")) limpo = limpo.replace(/\./g, "").replace(",", ".");
  if (!/^\d+(\.\d{1,2})?$/.test(limpo)) return NaN;
  return Number(limpo);
}

function hojeNoFuso(deslocamentoDias = 0) {
  const d = new Date(Date.now() + deslocamentoDias * 86400000);
  return new Intl.DateTimeFormat("en-CA", { timeZone: FUSO }).format(d); // AAAA-MM-DD
}

function escapar(texto) {
  const div = document.createElement("div");
  div.textContent = texto ?? "";
  return div.innerHTML;
}

/* ---------- Avisos ---------- */

function avisar(mensagem, { titulo, erro = false, duracao = 5000 } = {}) {
  const el = document.createElement("div");
  el.className = "aviso-flutuante" + (erro ? " erro" : "");
  el.setAttribute("role", erro ? "alert" : "status");
  el.innerHTML = (titulo ? `<strong>${escapar(titulo)}</strong>` : "") + escapar(mensagem);
  $("avisos").append(el);
  setTimeout(() => el.remove(), duracao);
}

/* ---------- Lista de contas ---------- */

async function carregarContas() {
  estado.contas = await api("/api/contas");
  const ativas = estado.contas.filter((c) => c.situacao === "ATIVA");
  $("resumo-contas").textContent = ativas.length;
  $("resumo-total").textContent = formatarMoeda(ativas.reduce((soma, c) => soma + Number(c.saldo), 0));
  desenharLista();
}

function desenharLista() {
  const termo = $("busca").value.trim().toLowerCase();
  const contas = estado.contas.filter(
    (c) => !termo || c.titular.toLowerCase().includes(termo) || c.numero.includes(termo)
  );
  const lista = $("lista-contas");
  if (!contas.length) {
    lista.innerHTML = `<li class="lista-vazia">${termo ? "Nenhuma conta encontrada." : "Nenhuma conta aberta ainda."}</li>`;
    return;
  }
  lista.innerHTML = contas
    .map((c) => {
      const atual = c.numero === estado.selecionada;
      const encerrada = c.situacao === "ENCERRADA";
      return `<li><button type="button" class="item-conta${encerrada ? " encerrada" : ""}" data-numero="${c.numero}"
          aria-current="${atual}">
          <span class="nome">${escapar(c.titular)}</span>
          <span class="saldo-item">${formatarMoeda(c.saldo)}</span>
          <span class="numero">Ag. ${c.agencia} · Conta ${c.numero}${encerrada ? " · encerrada" : ""}</span>
        </button></li>`;
    })
    .join("");
}

/* ---------- Conta selecionada ---------- */

async function selecionar(numero) {
  estado.selecionada = numero;
  history.replaceState(null, "", "#" + numero);
  desenharLista();
  const conta = estado.contas.find((c) => c.numero === numero);
  if (!conta) return;

  $("painel-vazio").hidden = true;
  $("painel-conta").hidden = false;
  $("conta-titular").textContent = conta.titular;
  $("conta-identificacao").textContent = `Agência ${conta.agencia} · Conta ${conta.numero}`;
  $("conta-saldo").textContent = formatarMoeda(conta.saldo);
  const encerrada = conta.situacao === "ENCERRADA";
  $("conta-situacao").textContent = encerrada ? "Encerrada" : "Ativa";
  $("operacoes").hidden = encerrada;

  const destino = $("destino");
  destino.innerHTML = estado.contas
    .filter((c) => c.numero !== numero && c.situacao === "ATIVA")
    .map((c) => `<option value="${c.numero}">${c.numero} · ${escapar(c.titular)}</option>`)
    .join("");
  trocarAba(estado.aba);
  await carregarExtrato();
}

async function carregarExtrato() {
  const numero = estado.selecionada;
  const parametros = new URLSearchParams();
  if ($("de").value) parametros.set("de", $("de").value);
  if ($("ate").value) parametros.set("ate", $("ate").value);
  const consulta = parametros.toString() ? "?" + parametros : "";

  const extrato = await api(`/api/contas/${numero}/extrato${consulta}`);
  $("de").value = extrato.de;
  $("ate").value = extrato.ate;
  $("exportar").href = `/api/contas/${numero}/extrato.csv?de=${extrato.de}&ate=${extrato.ate}`;
  $("t-inicial").textContent = formatarMoeda(extrato.saldoInicial);
  $("t-entradas").textContent = formatarMoeda(extrato.entradas);
  $("t-saidas").textContent = formatarMoeda(Number(extrato.saidas) > 0 ? -extrato.saidas : 0);
  $("t-final").textContent = formatarMoeda(extrato.saldoFinal);

  const linhas = [...extrato.lancamentos].reverse().map((l) => {
    const classe = Number(l.valor) >= 0 ? "credito" : "debito";
    return `<tr>
        <td class="data">${dataHora.format(new Date(l.dataHora))}</td>
        <td>${escapar(l.descricao)}</td>
        <td class="num ${classe}">${formatarMoeda(l.valor)}</td>
        <td class="num">${formatarMoeda(l.saldoApos)}</td>
      </tr>`;
  });
  linhas.push(`<tr class="saldo-anterior"><td></td><td>Saldo anterior</td><td></td>
      <td class="num">${formatarMoeda(extrato.saldoInicial)}</td></tr>`);
  $("lancamentos").innerHTML = extrato.lancamentos.length
    ? linhas.join("")
    : `<tr><td class="vazio-tabela" colspan="4">Nenhum lançamento no período.</td></tr>`;
}

/* ---------- Operações ---------- */

const ROTULOS = {
  deposito: "Depositar",
  saque: "Sacar",
  transferencia: "Transferir",
  encerrar: "Encerrar conta",
};

function trocarAba(aba) {
  estado.aba = aba;
  document.querySelectorAll(".aba").forEach((b) => b.setAttribute("aria-selected", String(b.dataset.aba === aba)));
  $("campo-destino").hidden = aba !== "transferencia";
  $("campo-valor").hidden = aba === "encerrar";
  $("aviso-encerrar").hidden = aba !== "encerrar";
  const botao = $("botao-operacao");
  botao.textContent = ROTULOS[aba];
  botao.classList.toggle("perigo", aba === "encerrar");
  botao.classList.toggle("primario", aba !== "encerrar");
  botao.disabled = aba === "transferencia" && !$("destino").options.length;
}

async function executarOperacao(evento) {
  evento.preventDefault();
  const numero = estado.selecionada;
  const aba = estado.aba;
  const botao = $("botao-operacao");

  let valor;
  if (aba !== "encerrar") {
    valor = lerValor($("valor").value);
    if (!(valor > 0)) {
      avisar("Digite um valor maior que zero, por exemplo 150,75.", { titulo: "Valor inválido", erro: true });
      $("valor").focus();
      return;
    }
  }

  botao.disabled = true;
  try {
    if (aba === "deposito") {
      await api(`/api/contas/${numero}/depositos`, { method: "POST", body: JSON.stringify({ valor }) });
      avisar(`${formatarMoeda(valor)} depositados na conta ${numero}.`, { titulo: "Depósito feito" });
    } else if (aba === "saque") {
      await api(`/api/contas/${numero}/saques`, { method: "POST", body: JSON.stringify({ valor }) });
      avisar(`${formatarMoeda(valor)} sacados da conta ${numero}.`, { titulo: "Saque feito" });
    } else if (aba === "transferencia") {
      const destino = $("destino").value;
      await api("/api/transferencias", { method: "POST", body: JSON.stringify({ origem: numero, destino, valor }) });
      avisar(`${formatarMoeda(valor)} enviados para a conta ${destino}.`, { titulo: "Transferência feita" });
    } else if (aba === "encerrar") {
      if (!confirm(`Encerrar a conta ${numero}? Essa ação não pode ser desfeita.`)) return;
      await api(`/api/contas/${numero}`, { method: "DELETE" });
      avisar(`A conta ${numero} foi encerrada.`, { titulo: "Conta encerrada" });
    }
    $("valor").value = "";
    await carregarContas();
    await selecionar(numero);
  } catch (erro) {
    avisar(erro.message, { titulo: erro.titulo, erro: true, duracao: 7000 });
  } finally {
    botao.disabled = false;
    trocarAba(estado.aba);
  }
}

/* ---------- Abrir conta ---------- */

function abrirDialogo() {
  $("form-abrir").reset();
  $("erro-abrir").hidden = true;
  $("dialogo-abrir").showModal();
  $("titular").focus();
}

async function abrirConta(evento) {
  evento.preventDefault();
  const erro = $("erro-abrir");
  const texto = $("saldo-inicial").value.trim();
  const saldoInicial = texto ? lerValor(texto) : 0;
  if (Number.isNaN(saldoInicial)) {
    erro.textContent = "Depósito inicial inválido. Use, por exemplo, 150,75 (ou deixe em branco).";
    erro.hidden = false;
    return;
  }
  try {
    const resposta = await api("/api/contas", {
      method: "POST",
      body: JSON.stringify({ titular: $("titular").value, agencia: $("agencia").value, saldoInicial }),
    });
    $("dialogo-abrir").close();
    avisar(resposta.mensagem, { titulo: "Conta aberta", duracao: 9000 });
    $("busca").value = "";
    await carregarContas();
    await selecionar(resposta.conta.numero);
  } catch (e) {
    erro.textContent = e.message;
    erro.hidden = false;
  }
}

/* ---------- Início ---------- */

function ligarEventos() {
  $("busca").addEventListener("input", desenharLista);
  $("lista-contas").addEventListener("click", (e) => {
    const botao = e.target.closest("[data-numero]");
    if (botao) selecionar(botao.dataset.numero).catch(mostrarErro);
  });
  document.querySelectorAll(".aba").forEach((b) => b.addEventListener("click", () => trocarAba(b.dataset.aba)));
  $("form-operacao").addEventListener("submit", executarOperacao);
  $("form-periodo").addEventListener("submit", (e) => {
    e.preventDefault();
    carregarExtrato().catch(mostrarErro);
  });
  $("botao-abrir").addEventListener("click", abrirDialogo);
  $("cancelar-abrir").addEventListener("click", () => $("dialogo-abrir").close());
  $("form-abrir").addEventListener("submit", abrirConta);
}

function mostrarErro(erro) {
  avisar(erro.message, { titulo: erro.titulo || "Erro", erro: true, duracao: 7000 });
}

async function iniciar() {
  ligarEventos();
  $("ate").value = hojeNoFuso();
  $("de").value = hojeNoFuso(-29);
  try {
    await carregarContas();
    const doEndereco = decodeURIComponent(location.hash.slice(1));
    const inicial = estado.contas.find((c) => c.numero === doEndereco) || estado.contas[0];
    if (inicial) await selecionar(inicial.numero);
  } catch (erro) {
    mostrarErro(erro);
  }
}

iniciar();
