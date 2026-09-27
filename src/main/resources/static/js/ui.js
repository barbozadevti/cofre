// Formatação, ícones, modal e avisos compartilhados pelas telas.

export const FUSO = "America/Sao_Paulo";
const moedaBr = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const dataHoraBr = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short", timeZone: FUSO });
const horaBr = new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit", timeZone: FUSO });
const diaLongo = new Intl.DateTimeFormat("pt-BR", { weekday: "long", day: "numeric", month: "long", timeZone: FUSO });
const chaveDia = new Intl.DateTimeFormat("en-CA", { timeZone: FUSO });

export const $ = (seletor, raiz = document) => raiz.querySelector(seletor);
export const $$ = (seletor, raiz = document) => [...raiz.querySelectorAll(seletor)];

export function moeda(valor) {
  return moedaBr.format(Number(valor)).replace(/ /g, " ");
}

export function moedaComSinal(valor) {
  const n = Number(valor);
  return (n > 0 ? "+ " : n < 0 ? "− " : "") + moeda(Math.abs(n));
}

export const dataHora = (iso) => dataHoraBr.format(new Date(iso));
export const hora = (iso) => horaBr.format(new Date(iso));
export const diaDoLancamento = (iso) => chaveDia.format(new Date(iso));

export function tituloDoDia(chave) {
  const hoje = chaveDia.format(new Date());
  const ontem = chaveDia.format(new Date(Date.now() - 86400000));
  if (chave === hoje) return "Hoje";
  if (chave === ontem) return "Ontem";
  const texto = diaLongo.format(new Date(chave + "T12:00:00-03:00"));
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

export function hojeISO(deslocamentoDias = 0) {
  return chaveDia.format(new Date(Date.now() + deslocamentoDias * 86400000));
}

/** Lê "1.234,56", "1234,56" ou "1234.56"; devolve NaN se não for um valor válido. */
export function lerValor(texto) {
  let limpo = String(texto ?? "").trim().replace(/R\$|\s/g, "");
  if (limpo.includes(",")) limpo = limpo.replace(/\./g, "").replace(",", ".");
  if (!/^\d+(\.\d{1,2})?$/.test(limpo)) return NaN;
  return Number(limpo);
}

export function escapar(texto) {
  return String(texto ?? "")
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

export function iniciais(nome) {
  const partes = String(nome || "?").trim().split(/\s+/);
  return (partes[0][0] + (partes.length > 1 ? partes[partes.length - 1][0] : "")).toUpperCase();
}

// ---------- Ícones (traço, 24x24) ----------

const caminhos = {
  inicio: '<path d="M3 11.5 12 4l9 7.5"/><path d="M5 10v10h14V10"/>',
  pix: '<path d="m12 3 4.5 4.5L12 12 7.5 7.5z"/><path d="m12 12 4.5 4.5L12 21l-4.5-4.5z"/><path d="M3 12l4.5-4.5L12 12l-4.5 4.5z"/><path d="m12 12 4.5-4.5L21 12l-4.5 4.5z"/>',
  extrato: '<path d="M6 3h12v18l-3-2-3 2-3-2-3 2z"/><path d="M9 8h6M9 12h6"/>',
  caixinha: '<rect x="3" y="7" width="18" height="13" rx="3"/><path d="M8 7V5a4 4 0 0 1 8 0v2"/><circle cx="12" cy="13.5" r="1.5"/>',
  cartao: '<rect x="2.5" y="5" width="19" height="14" rx="2.5"/><path d="M2.5 10h19M6.5 15h4"/>',
  perfil: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  sair: '<path d="M15 4h4v16h-4"/><path d="M10 8l-4 4 4 4M6 12h10"/>',
  olho: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
  olhoFechado: '<path d="M3 3l18 18"/><path d="M10.6 5.1A10 10 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3.2 4.2M6.6 6.6C3.8 8.3 2 12 2 12s3.5 7 10 7a9.6 9.6 0 0 0 4.4-1"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/>',
  transferir: '<path d="M4 8h14l-4-4M20 16H6l4 4"/>',
  receber: '<path d="M12 4v12M6 10l6 6 6-6"/><path d="M4 20h16"/>',
  enviar: '<path d="M12 20V8M6 14l6-6 6 6"/><path d="M4 4h16"/>',
  qr: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><path d="M14 14h3v3h-3zM18 18h3v3M14 21h1"/>',
  colar: '<rect x="8" y="3" width="8" height="4" rx="1"/><path d="M8 5H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/>',
  deposito: '<path d="M12 3v12M7 10l5 5 5-5"/><rect x="3" y="17" width="18" height="4" rx="1"/>',
  saque: '<path d="M12 15V3M7 8l5-5 5 5"/><rect x="3" y="17" width="18" height="4" rx="1"/>',
  juros: '<path d="M19 5 5 19"/><circle cx="7" cy="7" r="2.5"/><circle cx="17" cy="17" r="2.5"/>',
  abertura: '<path d="M12 3l9 5v2H3V8z"/><path d="M5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 21h18"/>',
  painel: '<rect x="3" y="3" width="8" height="10" rx="2"/><rect x="13" y="3" width="8" height="6" rx="2"/><rect x="13" y="11" width="8" height="10" rx="2"/><rect x="3" y="15" width="8" height="6" rx="2"/>',
  contas: '<path d="M4 7h16M4 12h16M4 17h10"/>',
  novo: '<path d="M12 5v14M5 12h14"/>',
  auditoria: '<path d="M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z"/><path d="m9 12 2 2 4-4"/>',
  balcao: '<path d="M3 21h18M5 21V10M19 21V10M3 10h18L12 3z"/><path d="M9 21v-5h6v5"/>',
  ok: '<path d="m5 12 5 5 9-10"/>',
  fechar: '<path d="M6 6l12 12M18 6 6 18"/>',
  copiar: '<rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/>',
  cadeado: '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
  escudo: '<path d="M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z"/>',
  relogio: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  lixo: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
  imprimir: '<path d="M7 9V3h10v6"/><rect x="3" y="9" width="18" height="8" rx="2"/><path d="M7 14h10v7H7z"/>',
  baixar: '<path d="M12 4v12M7 11l5 5 5-5M4 20h16"/>',
};

export function icone(nome) {
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${caminhos[nome] || ""}</svg>`;
}

/** Ícone e sentido de cada tipo de lançamento. */
export function aparenciaDoLancamento(tipo) {
  const mapa = {
    PIX_ENVIADO: "pix", PIX_RECEBIDO: "pix", TRANSFERENCIA_ENVIADA: "transferir", TRANSFERENCIA_RECEBIDA: "transferir",
    DEPOSITO: "deposito", SAQUE: "saque", CAIXINHA_GUARDADO: "caixinha", CAIXINHA_RESGATADO: "caixinha",
    JUROS_CHEQUE_ESPECIAL: "juros", ABERTURA: "abertura",
  };
  return mapa[tipo] || "extrato";
}

// ---------- Avisos ----------

export function avisar(mensagem, { titulo, erro = false, duracao = 5000 } = {}) {
  const el = document.createElement("div");
  el.className = "aviso" + (erro ? " erro" : "");
  el.setAttribute("role", erro ? "alert" : "status");
  el.innerHTML = (titulo ? `<strong>${escapar(titulo)}</strong>` : "") + escapar(mensagem);
  $("#avisos").append(el);
  setTimeout(() => el.remove(), duracao);
}

export function avisarErro(erro) {
  avisar(erro.message, { titulo: erro.titulo || "Erro", erro: true, duracao: 7000 });
}

// ---------- Modal ----------

/** Abre o modal com o HTML do corpo; devolve o elemento do corpo para ligar eventos. */
export function abrirModal(titulo, corpoHtml) {
  const modal = $("#modal");
  modal.innerHTML = `<div class="modal-corpo">
      <div class="modal-topo"><h2 id="modal-titulo">${escapar(titulo)}</h2>
        <button type="button" class="icone-botao" data-fechar aria-label="Fechar">${icone("fechar")}</button></div>
      <div class="modal-conteudo">${corpoHtml}</div>
    </div>`;
  $("[data-fechar]", modal).addEventListener("click", () => modal.close());
  if (!modal.open) modal.showModal();
  return $(".modal-conteudo", modal);
}

export function fecharModal() {
  const modal = $("#modal");
  if (modal.open) modal.close();
}

/** Confirmação no estilo do app (sem window.confirm). */
export function confirmar(titulo, mensagem, { rotulo = "Confirmar", perigo = false } = {}) {
  return new Promise((resolver) => {
    const corpo = abrirModal(titulo, `<div class="formulario"><p class="suave">${escapar(mensagem)}</p>
      <div class="acoes"><button type="button" class="botao fantasma" data-nao>Cancelar</button>
      <button type="button" class="botao ${perigo ? "perigo" : "primario"}" data-sim>${escapar(rotulo)}</button></div></div>`);
    const modal = $("#modal");
    let respondeu = false;
    const responder = (valor) => { respondeu = true; modal.close(); resolver(valor); };
    $("[data-sim]", corpo).addEventListener("click", () => responder(true));
    $("[data-nao]", corpo).addEventListener("click", () => responder(false));
    modal.addEventListener("close", () => { if (!respondeu) resolver(false); }, { once: true });
  });
}

/** Mostra um comprovante (Pix, transferência, depósito...). */
export function mostrarComprovante(c) {
  const parte = (rotulo, p) => p ? `<div class="parte"><span class="rotulo">${rotulo}</span>
      <strong>${escapar(p.nome)}</strong><span class="suave">CPF ${escapar(p.cpfMascarado)} · Ag. ${escapar(p.agencia)} · Conta ${escapar(p.conta)}</span>
      <span class="suave">Cofre · ISPB 31415926</span></div>` : "";
  const corpo = abrirModal(c.titulo, `<div class="comprovante">
      <div class="cabeca"><span class="ok">${icone("ok")}</span><span class="suave">Transação concluída</span>
        <span class="valor">${moeda(c.valor)}</span><span class="suave">${dataHora(c.dataHora)}</span></div>
      ${parte("Quem pagou", c.origem)}${parte("Quem recebeu", c.destino)}
      ${c.mensagem ? `<dl><div><dt>Mensagem</dt><dd>${escapar(c.mensagem)}</dd></div></dl>` : ""}
      <p class="autenticacao">Autenticação<br>${escapar(c.idTransacao)}</p>
      <div class="acoes"><button type="button" class="botao" data-imprimir>${icone("imprimir")} Imprimir ou salvar PDF</button></div>
    </div>`);
  $("[data-imprimir]", corpo).addEventListener("click", () => window.print());
}

/** Gráfico de barras (entradas x saídas por mês) em SVG, sem biblioteca. */
export function graficoMensal(meses) {
  const largura = 560;
  const base = 170;
  const maximo = Math.max(1, ...meses.flatMap((m) => [Number(m.entradas), Number(m.saidas)]));
  const passo = largura / meses.length;
  const barra = Math.min(22, passo / 3.2);
  const nomes = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"];
  const colunas = meses.map((m, i) => {
    const x = i * passo + passo / 2;
    const he = (Number(m.entradas) / maximo) * (base - 16);
    const hs = (Number(m.saidas) / maximo) * (base - 16);
    const nome = nomes[Number(m.mes.slice(5, 7)) - 1];
    return `<g><title>${nome}: entrou ${moeda(m.entradas)}, saiu ${moeda(m.saidas)}</title>
      <rect class="barra-entrada" x="${x - barra - 2}" y="${base - he}" width="${barra}" height="${Math.max(he, 1)}" rx="4"/>
      <rect class="barra-saida" x="${x + 2}" y="${base - hs}" width="${barra}" height="${Math.max(hs, 1)}" rx="4"/>
      </g>`;
  });
  const rotulos = meses.map((m) => `<span>${nomes[Number(m.mes.slice(5, 7)) - 1]}</span>`).join("");
  return `<svg viewBox="0 0 ${largura} ${base + 2}" role="img" aria-label="Entradas e saídas dos últimos 6 meses">
      <line x1="0" y1="${base}" x2="${largura}" y2="${base}" stroke="#2a2a31"/>${colunas.join("")}</svg>
    <div class="meses-grafico" aria-hidden="true">${rotulos}</div>`;
}

/** Lista de lançamentos agrupada por dia; itens com comprovante viram botões. */
export function listaDeLancamentos(lancamentos, { agrupar = true } = {}) {
  if (!lancamentos.length) return `<div class="vazio">${icone("extrato")}<span>Nenhuma movimentação no período.</span></div>`;
  let diaAtual = null;
  return lancamentos.map((l) => {
    const valor = Number(l.valor);
    const sentido = valor >= 0 ? "entrada" : "saida";
    const dia = diaDoLancamento(l.dataHora);
    const cabecalho = agrupar && dia !== diaAtual ? `<div class="dia">${tituloDoDia(dia)}</div>` : "";
    diaAtual = dia;
    const comComprovante = /PIX|TRANSFERENCIA/.test(l.tipo);
    const tag = comComprovante ? "button" : "div";
    const atributos = comComprovante ? ` type="button" data-comprovante="${escapar(l.idTransacao)}"` : "";
    return `${cabecalho}<${tag} class="item"${atributos}>
        <span class="icone ${sentido}">${icone(aparenciaDoLancamento(l.tipo))}</span>
        <span class="texto"><span class="titulo">${escapar(l.descricao)}</span>
          <span class="sub">${hora(l.dataHora)}${l.mensagem ? " · " + escapar(l.mensagem) : ""}</span></span>
        <span class="valor ${sentido} sensivel">${moedaComSinal(valor)}<small>saldo ${moeda(l.saldoApos)}</small></span>
      </${tag}>`;
  }).join("");
}
