// Copiloto financeiro: previsão de saldo para 30 dias e o Escudo de juros (o diferencial do Cofre).
import { api } from "./api.js";
import { $, avisar, avisarErro, escapar, icone, moeda } from "./ui.js";

const DIA = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", timeZone: "UTC" });
const STATUS = { TRANQUILO: "Tudo em ordem", ATENCAO: "Atenção", PROTEGIDO: "Protegido pelo Escudo" };

function dia(iso) {
  return DIA.format(new Date(iso + "T00:00:00Z"));
}

/** Linha do saldo previsto: acima de zero em dourado, abaixo em vermelho, com marcas nos eventos. */
function graficoPrevisao(c) {
  const largura = 640;
  const altura = 150;
  const margem = { topo: 12, base: 22, lado: 6 };
  const serie = [{ dia: null, saldo: Number(c.saldoAtual) }, ...c.pontos.map((p) => ({ dia: p.dia, saldo: Number(p.saldo) }))];
  const valores = serie.map((p) => p.saldo);
  const maximo = Math.max(0, ...valores);
  const minimo = Math.min(0, ...valores);
  const faixa = maximo - minimo || 1;
  const x = (i) => margem.lado + (i / (serie.length - 1)) * (largura - margem.lado * 2);
  const y = (v) => margem.topo + ((maximo - v) / faixa) * (altura - margem.topo - margem.base);
  const zero = y(0);
  const linha = serie.map((p, i) => `${i ? "L" : "M"}${x(i).toFixed(1)},${y(p.saldo).toFixed(1)}`).join(" ");
  const area = `${linha} L${x(serie.length - 1).toFixed(1)},${zero.toFixed(1)} L${x(0).toFixed(1)},${zero.toFixed(1)} Z`;
  const indice = new Map(serie.map((p, i) => [p.dia, i]));
  const marcas = c.eventos.map((e) => {
    const i = indice.get(e.dia);
    if (i === undefined) return "";
    const entrada = Number(e.valor) > 0;
    return `<g class="marca ${entrada ? "m-entrada" : "m-saida"}"><title>${dia(e.dia)} · ${escapar(e.descricao.split(" · ")[0])}: ${moeda(e.valor)} (saldo ${moeda(e.saldoApos)})</title>
      <circle cx="${x(i).toFixed(1)}" cy="${y(Number(e.saldoApos)).toFixed(1)}" r="4.5"/></g>`;
  }).join("");
  const meio = Math.floor(serie.length / 2);
  return `<svg viewBox="0 0 ${largura} ${altura}" role="img" aria-label="Saldo previsto para os próximos 30 dias" preserveAspectRatio="none">
    <defs>
      <clipPath id="acima"><rect x="0" y="0" width="${largura}" height="${zero}"/></clipPath>
      <clipPath id="abaixo"><rect x="0" y="${zero}" width="${largura}" height="${altura - zero}"/></clipPath>
      <linearGradient id="g-ouro" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#d9b56d" stop-opacity=".35"/><stop offset="1" stop-color="#d9b56d" stop-opacity="0"/></linearGradient>
      <linearGradient id="g-vermelho" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#ff7a7a" stop-opacity=".45"/><stop offset="1" stop-color="#ff7a7a" stop-opacity=".05"/></linearGradient>
    </defs>
    <path d="${area}" fill="url(#g-ouro)" clip-path="url(#acima)"/>
    <path d="${area}" fill="url(#g-vermelho)" clip-path="url(#abaixo)"/>
    <line x1="0" x2="${largura}" y1="${zero}" y2="${zero}" class="linha-zero"/>
    <path d="${linha}" class="linha-saldo" clip-path="url(#acima)"/>
    <path d="${linha}" class="linha-saldo negativa" clip-path="url(#abaixo)"/>
    ${marcas}
    <text x="${margem.lado}" y="${altura - 4}" class="eixo">hoje</text>
    <text x="${x(meio)}" y="${altura - 4}" class="eixo" text-anchor="middle">${dia(serie[meio].dia)}</text>
    <text x="${largura - margem.lado}" y="${altura - 4}" class="eixo" text-anchor="end">${dia(serie[serie.length - 1].dia)}</text>
  </svg>`;
}

function proximos(c) {
  if (!c.eventos.length) {
    return `<p class="suave">Ainda não há movimentos que se repitam todo mês para prever. Com 2 meses de uso, o Copiloto aprende sua rotina.</p>`;
  }
  return `<ul class="proximos">${c.eventos.slice(0, 4).map((e) => {
    const entrada = Number(e.valor) > 0;
    return `<li><span class="data">${dia(e.dia)}</span><span class="descricao">${escapar(e.descricao.split(" · ")[0])}</span>
      <span class="valor ${entrada ? "entrada" : "saida"} sensivel">${entrada ? "+" : "−"} ${moeda(Math.abs(Number(e.valor)))}</span>
      <span class="apos sensivel ${Number(e.saldoApos) < 0 ? "saida" : ""}">saldo ${moeda(e.saldoApos)}</span></li>`;
  }).join("")}</ul>`;
}

function painelEscudo(e) {
  if (!e.disponivel) {
    return `<aside class="escudo-painel">
      <div class="escudo-titulo">${icone("escudo")}<strong>Escudo de juros</strong></div>
      <p>Quando a conta ficaria negativa, o Escudo cobre com a sua poupança. Você não paga 8% ao mês tendo dinheiro guardado.</p>
      <p class="suave">Abra sua poupança (logo abaixo) para ligar o Escudo.</p>
    </aside>`;
  }
  return `<aside class="escudo-painel ${e.ativo ? "ligado" : ""}">
    <div class="escudo-titulo">${icone("escudo")}<strong>Escudo de juros</strong>
      <button type="button" class="interruptor" role="switch" aria-checked="${e.ativo}" data-escudo aria-label="Escudo de juros"><span></span></button></div>
    <p>${e.ativo
      ? "Ligado. Se a conta ficar negativa, a poupança cobre na hora, sem juros."
      : "Desligado. Ligue para cobrir o saldo negativo com a poupança, sem juros de 8% ao mês."}</p>
    <div class="escudo-numeros">
      <div><small>Poupança disponível</small><strong class="sensivel">${moeda(e.saldoDaPoupanca)}</strong></div>
      <div><small>Coberturas</small><strong>${e.coberturas}</strong></div>
      <div><small>Já coberto</small><strong class="sensivel">${moeda(e.totalCoberto)}</strong></div>
      <div><small>Juros evitados</small><strong class="sensivel entrada">${moeda(e.economiaEstimada)}</strong></div>
    </div>
  </aside>`;
}

/** Desenha o Copiloto no elemento indicado e liga o interruptor do Escudo. */
export async function montarCopiloto(alvo, numero, recarregar) {
  try {
    const c = await api(`/api/app/contas/${numero}/copiloto`);
    alvo.innerHTML = `<section class="copiloto nivel-${c.nivel}" aria-label="Copiloto financeiro">
      <div class="copiloto-cabeca">
        <span class="copiloto-marca">${icone("copiloto")} Copiloto</span>
        <span class="pilula">próximos 30 dias</span>
        <span class="status-copiloto">${STATUS[c.nivel]}</span>
      </div>
      <div class="copiloto-corpo">
        <div class="copiloto-previsao">
          <h2>${escapar(c.titulo)}</h2>
          <p class="copiloto-mensagem">${escapar(c.mensagem)}</p>
          <div class="grafico-previsao sensivel">${graficoPrevisao(c)}</div>
          ${proximos(c)}
        </div>
        ${painelEscudo(c.escudo)}
      </div>
    </section>`;
    $("[data-escudo]", alvo)?.addEventListener("click", async (e) => {
      const botao = e.currentTarget;
      botao.disabled = true;
      try {
        const ligado = botao.getAttribute("aria-checked") === "true";
        await api(`/api/app/contas/${numero}/copiloto/escudo`, { metodo: ligado ? "DELETE" : "POST" });
        avisar(ligado ? "Escudo de juros desligado." : "Escudo de juros ligado: o saldo negativo agora é coberto pela poupança.");
        recarregar();
      } catch (erro) {
        avisarErro(erro);
        botao.disabled = false;
      }
    });
  } catch (erro) {
    alvo.innerHTML = "";
    console.warn("Copiloto indisponível", erro);
  }
}
