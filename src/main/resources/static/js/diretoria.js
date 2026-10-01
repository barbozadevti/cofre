// Visão executiva (perfil Diretoria): o banco inteiro em uma tela.
import { api } from "./api.js";
import { escapar, icone, moeda } from "./ui.js";
import { botaoPrivacidade } from "./app.js";

const MESES = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"];
const nomeDoMes = (m) => MESES[Number(m.slice(5, 7)) - 1];

function compacto(valor) {
  const v = Number(valor);
  if (Math.abs(v) >= 1_000_000) return "R$ " + (v / 1_000_000).toLocaleString("pt-BR", { maximumFractionDigits: 1 }) + " mi";
  if (Math.abs(v) >= 1_000) return "R$ " + (v / 1_000).toLocaleString("pt-BR", { maximumFractionDigits: 1 }) + " mil";
  return moeda(v);
}

/** Barras agrupadas: receita de juros (dourado) x custo do rendimento (cinza), por mês. */
function graficoResultado(meses) {
  const largura = 560;
  const base = 160;
  const maximo = Math.max(1, ...meses.flatMap((m) => [Number(m.receitaDeJuros), Number(m.custoDoRendimento)]));
  const passo = largura / meses.length;
  const barra = Math.min(24, passo / 3.2);
  const colunas = meses.map((m, i) => {
    const x = i * passo + passo / 2;
    const hr = (Number(m.receitaDeJuros) / maximo) * (base - 14);
    const hc = (Number(m.custoDoRendimento) / maximo) * (base - 14);
    return `<g><title>${nomeDoMes(m.mes)}: juros ${moeda(m.receitaDeJuros)}, rendimento pago ${moeda(m.custoDoRendimento)}</title>
      <rect class="barra-entrada" x="${x - barra - 2}" y="${base - hr}" width="${barra}" height="${Math.max(hr, 1)}" rx="4"/>
      <rect class="barra-saida" x="${x + 2}" y="${base - hc}" width="${barra}" height="${Math.max(hc, 1)}" rx="4"/></g>`;
  }).join("");
  return `<svg viewBox="0 0 ${largura} ${base + 2}" role="img" aria-label="Receita de juros e custo do rendimento por mês">
      <line x1="0" y1="${base}" x2="${largura}" y2="${base}" stroke="#2a2a31"/>${colunas}</svg>
    <div class="meses-grafico" aria-hidden="true">${meses.map((m) => `<span>${nomeDoMes(m.mes)}</span>`).join("")}</div>`;
}

/** Rosca da composição da custódia. */
function rosca(partes) {
  const total = partes.reduce((s, p) => s + p.valor, 0) || 1;
  const raio = 52;
  const circ = 2 * Math.PI * raio;
  let acumulado = 0;
  const arcos = partes.map((p) => {
    const fatia = (p.valor / total) * circ;
    const arco = `<circle r="${raio}" cx="70" cy="70" class="${p.classe}" stroke-dasharray="${fatia} ${circ - fatia}" stroke-dashoffset="${-acumulado}"><title>${p.nome}: ${moeda(p.valor)}</title></circle>`;
    acumulado += fatia;
    return arco;
  }).join("");
  return `<div class="rosca">
    <svg viewBox="0 0 140 140" role="img" aria-label="Composição da custódia"><g transform="rotate(-90 70 70)">${arcos}</g>
      <text x="70" y="66" text-anchor="middle" class="rosca-rotulo">custódia</text>
      <text x="70" y="84" text-anchor="middle" class="rosca-valor sensivel">${compacto(total)}</text></svg>
    <ul>${partes.map((p) => `<li><i class="${p.classe}"></i><span>${p.nome}<small>${((p.valor / total) * 100).toFixed(1).replace(".", ",")}% da custódia</small></span><strong class="sensivel">${moeda(p.valor)}</strong></li>`).join("")}</ul>
  </div>`;
}

function kpi(rotulo, valor, detalhe, nomeIcone) {
  return `<section class="kpi kpi-executivo"><div class="kpi-icone">${icone(nomeIcone)}</div><small>${rotulo}</small>
    <strong class="sensivel">${valor}</strong><span class="suave">${detalhe}</span></section>`;
}

function numero(rotulo, valor, classe = "") {
  return `<div class="numero"><small>${rotulo}</small><strong class="sensivel ${classe}">${valor}</strong></div>`;
}

export async function painel(principal) {
  const p = await api("/api/diretoria/painel");
  const hoje = new Intl.DateTimeFormat("pt-BR", { dateStyle: "long", timeZone: "America/Sao_Paulo" }).format(new Date());
  const util = Number(p.credito.utilizacao);
  principal.innerHTML = `
    <div class="topo"><div><h1>Visão executiva</h1><p>${hoje} · últimos 30 dias · dados de demonstração</p></div>
      <div class="topo-acoes">${botaoPrivacidade()}</div></div>

    <section class="leituras" aria-label="Leitura do período">
      <div class="leituras-titulo">${icone("diretoria")}<strong>Leitura do período</strong></div>
      <ul>${p.leituras.map((l) => `<li class="tom-${l.tom}">${escapar(l.texto)}</li>`).join("")}</ul>
    </section>

    <div class="grade-4">
      ${kpi("Custódia total", compacto(p.custodia.total), `${p.base.clientes} clientes · ${p.base.contasCorrentes + p.base.poupancas} contas`, "abertura")}
      ${kpi("Margem de juros", moeda(p.resultado.margem), `${moeda(p.resultado.receitaDeJuros)} recebidos − ${moeda(p.resultado.custoDoRendimento)} pagos`, "rendimento")}
      ${kpi("Volume de Pix", compacto(p.pix.volume), `${p.pix.quantidade} envios · ticket ${moeda(p.pix.ticketMedio)}`, "pix")}
      ${kpi("Salários trazidos", moeda(p.salario.folhaMensal), `${p.salario.portabilidadesConcluidas} concluída(s) · ${p.salario.emAndamento} em andamento`, "salario")}
    </div>

    <div class="grade-2 espaco-topo">
      <section class="bloco">
        <div class="bloco-topo"><h2>Juros recebidos x rendimento pago</h2>
          <div class="legenda"><span><i class="l-entrada"></i>Juros do cheque especial</span><span><i class="l-saida"></i>Rendimento da poupança</span></div></div>
        <div class="grafico sensivel">${graficoResultado(p.meses)}</div>
      </section>
      <section class="bloco">
        <div class="bloco-topo"><h2>Onde está o dinheiro dos clientes</h2></div>
        ${rosca([
          { nome: "Conta corrente", valor: Number(p.custodia.contaCorrente), classe: "f-corrente" },
          { nome: "Poupança", valor: Number(p.custodia.poupanca), classe: "f-poupanca" },
          { nome: "Caixinhas", valor: Number(p.custodia.caixinhas), classe: "f-caixinhas" },
        ])}
      </section>
    </div>

    <div class="grade-3 espaco-topo">
      <section class="bloco bloco-risco">
        <div class="bloco-topo"><h2>Cheque especial</h2><span class="etiqueta ${util > 40 ? "alerta" : ""}">${String(util).replace(".", ",")}% em uso</span></div>
        <div class="medidor ${util > 40 ? "alerta" : ""}"><span data-largura="${Math.min(100, util)}"></span></div>
        <div class="numeros">${numero("Limite concedido", compacto(p.credito.limiteConcedido))}${numero("Em uso", moeda(p.credito.emUso), "saida")}
          ${numero("Clientes no negativo", p.credito.clientesNoNegativo)}</div>
      </section>
      <section class="bloco bloco-risco">
        <div class="bloco-topo"><h2>Antifraude Pix</h2><span class="etiqueta">nota de 0 a 100</span></div>
        <div class="numeros">${numero("Pix retidos", p.antifraude.retidos)}${numero("Confirmados", p.antifraude.confirmados)}
          ${numero("Golpes evitados*", p.antifraude.desistencias, "entrada")}</div>
        <p class="suave pequeno">* retidos que o cliente decidiu não enviar.</p>
      </section>
      <section class="bloco bloco-risco destaque-escudo">
        <div class="bloco-topo"><h2>Escudo de juros</h2><span class="etiqueta">diferencial</span></div>
        <div class="numeros">${numero("Contas protegidas", p.escudo.contasProtegidas)}${numero("Coberturas", p.escudo.coberturas)}
          ${numero("Saldo negativo coberto", moeda(p.escudo.valorCoberto))}</div>
        <p class="suave pequeno">Receita de juros renunciada: até ${moeda(p.escudo.jurosRenunciados)} por mês, trocada por retenção e confiança.</p>
      </section>
    </div>

    <section class="bloco espaco-topo">
      <div class="bloco-topo"><h2>Concentração da custódia</h2><span class="suave">5 maiores clientes: ${String(p.concentracao.top5).replace(".", ",")}%</span></div>
      <ul class="ranking">${p.concentracao.maiores.map((c) => `<li><span>${escapar(c.nome)}</span>
        <div class="medidor"><span data-largura="${Number(c.participacao)}"></span></div>
        <strong class="sensivel">${moeda(c.saldo)}</strong><small>${String(c.participacao).replace(".", ",")}%</small></li>`).join("")}</ul>
    </section>`;
  principal.querySelectorAll("[data-largura]").forEach((el) => { el.style.width = el.dataset.largura + "%"; });
}
