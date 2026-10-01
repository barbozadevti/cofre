// Traga seu salário: portabilidade (simulada), acompanhamento e "Pague-se primeiro".
import { api } from "./api.js";
import { $, avisar, avisarErro, confirmar, escapar, icone, lerValor, moeda } from "./ui.js";
import { botaoPrivacidade } from "./app.js";

const DATA = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short", timeZone: "America/Sao_Paulo" });

function linhaDoTempo(p) {
  return `<ol class="linha-do-tempo">${p.etapas.map((e) => `<li class="${e.feita ? "feita" : ""}">
      <span class="ponto">${e.feita ? icone("ok") : ""}</span>
      <div><strong>${escapar(e.nome)}</strong><small>${e.feita ? "" : "previsto para "}${DATA.format(new Date(e.quando))}</small></div>
    </li>`).join("")}</ol>`;
}

function cartaoPortabilidade(p, s) {
  const concluida = p.situacao === "CONCLUIDA";
  return `<section class="bloco cartao-portabilidade ${concluida ? "concluida" : ""}">
    <div class="bloco-topo"><h2>Sua portabilidade</h2><span class="etiqueta ${concluida ? "" : "em-andamento"}">${escapar(p.situacaoNome)}</span></div>
    ${linhaDoTempo(p)}
    <dl class="ficha">
      <div><dt>De</dt><dd>${escapar(p.bancoNome)} (${p.banco})</dd></div>
      <div><dt>Empregador</dt><dd>${escapar(p.empregador)}</dd></div>
      <div><dt>CNPJ</dt><dd class="mono">${p.cnpj}</dd></div>
      <div><dt>Salário</dt><dd class="sensivel">${moeda(p.salario)} · todo dia ${p.diaPagamento}</dd></div>
      <div><dt>Protocolo</dt><dd class="mono">${p.protocolo}</dd></div>
    </dl>
    ${concluida
      ? `<p class="destaque-texto">${icone("ok")} Seu salário já cai no Cofre. ${s.mesesRecebidos
          ? `Recebido ${s.mesesRecebidos} ${s.mesesRecebidos === 1 ? "vez" : "vezes"}, <span class="sensivel">${moeda(s.salariosRecebidos)}</span> no total.` : "O primeiro chega no próximo dia de pagamento."}</p>`
      : `<div class="acoes"><button type="button" class="botao fantasma" data-cancelar="${p.id}">Cancelar pedido</button></div>`}
  </section>`;
}

function formulario(s) {
  return `<section class="bloco">
    <div class="bloco-topo"><h2>Pedir a portabilidade</h2><span class="suave">leva cerca de 2 minutos</span></div>
    <form class="formulario" novalidate data-portabilidade>
      <div class="linha-campos">
        <label class="campo"><span>Onde o salário cai hoje</span><select name="banco" required>
          ${s.bancos.map((b) => `<option value="${b.codigo}">${b.codigo} · ${escapar(b.nome)}</option>`).join("")}</select></label>
        <label class="campo"><span>Dia do pagamento</span><input name="dia" type="number" min="1" max="31" value="5" required></label>
      </div>
      <label class="campo"><span>Empresa onde você trabalha</span><input name="empregador" maxlength="120" placeholder="Razão social" required></label>
      <div class="linha-campos">
        <label class="campo"><span>CNPJ da empresa</span><input name="cnpj" inputmode="numeric" placeholder="00.000.000/0000-00" required></label>
        <label class="campo"><span>Salário líquido</span><input name="salario" inputmode="decimal" placeholder="0,00" required></label>
      </div>
      <p class="suave">Simulação: nenhum pedido é enviado a outro banco. Na vida real, a empresa não fica sabendo e a portabilidade não tem custo.</p>
      <p class="erro-form" hidden></p>
      <div class="acoes"><button class="botao primario" type="submit">${icone("salario")} Trazer meu salário</button></div>
    </form>
  </section>`;
}

function pagueSePrimeiro(s, salarioBase) {
  if (!s.temPoupanca) {
    return `<section class="bloco destaque-poupanca">
      <div class="bloco-topo"><h2>Pague-se primeiro</h2><span class="etiqueta">automático</span></div>
      <p class="suave">Guarde uma parte de cada salário antes de gastar. Abra sua poupança na tela de início para ligar.</p>
    </section>`;
  }
  return `<section class="bloco destaque-poupanca">
    <div class="bloco-topo"><h2>Pague-se primeiro</h2><span class="etiqueta">${s.reservaPercentual ? `${s.reservaPercentual}% ligado` : "desligado"}</span></div>
    <p class="suave">Assim que o salário cai, a parte escolhida vai para a poupança, antes de qualquer gasto. Vale também para Pix com "salário" na mensagem.</p>
    <form data-reserva class="reserva">
      <input type="range" name="percentual" min="0" max="50" step="5" value="${s.reservaPercentual}" aria-label="Porcentagem do salário">
      <div class="reserva-previa">
        <strong data-pct>${s.reservaPercentual}%</strong>
        <span>de <span class="sensivel">${moeda(salarioBase)}</span> = <strong class="sensivel entrada" data-guardado>${moeda(salarioBase * s.reservaPercentual / 100)}</strong> por mês na poupança</span>
        <small class="suave" data-ano>${moeda(salarioBase * s.reservaPercentual / 100 * 12)} em um ano, sem contar o rendimento</small>
      </div>
      <div class="acoes"><button class="botao primario" type="submit">Salvar</button></div>
    </form>
  </section>`;
}

export async function salario(principal, ctx) {
  const s = await api("/api/app/salario");
  const ativa = s.portabilidades.find((p) => p.situacao !== "CANCELADA");
  const salarioBase = ativa ? Number(ativa.salario) : 3000;
  principal.innerHTML = `
    <div class="topo"><div><h1>Traga seu salário</h1><p>Conta corrente ${s.conta}</p></div><div class="topo-acoes">${botaoPrivacidade()}</div></div>
    <section class="hero-salario">
      <div>
        <span class="rotulo">Portabilidade de salário</span>
        <h2>Seu salário cai aqui, sem trocar de emprego nem falar com o RH.</h2>
        <p>A empresa continua pagando no banco de sempre, e o dinheiro vem para o Cofre automaticamente, no mesmo dia.</p>
      </div>
      <ul class="vantagens">
        <li>${icone("escudo")}<span><strong>Escudo de juros</strong>Saldo negativo coberto pela poupança, sem juros</span></li>
        <li>${icone("caixinha")}<span><strong>Pague-se primeiro</strong>Uma parte do salário guardada sozinha</span></li>
        <li>${icone("copiloto")}<span><strong>Copiloto</strong>Previsão do seu mês, com aviso antes do aperto</span></li>
      </ul>
    </section>
    <div class="grade-2">
      ${ativa ? cartaoPortabilidade(ativa, s) : formulario(s)}
      ${pagueSePrimeiro(s, salarioBase)}
    </div>`;

  $("[data-cancelar]", principal)?.addEventListener("click", async (e) => {
    if (!(await confirmar("Cancelar a portabilidade?", "O salário continua caindo no banco de origem.", { rotulo: "Cancelar pedido", perigo: true }))) return;
    try {
      await api(`/api/app/salario/portabilidade/${e.currentTarget.dataset.cancelar}`, { metodo: "DELETE" });
      avisar("Pedido cancelado.");
      ctx.recarregar();
    } catch (erro) {
      avisarErro(erro);
    }
  });

  const form = $("[data-portabilidade]", principal);
  form?.addEventListener("submit", async (e) => {
    e.preventDefault();
    const erro = $(".erro-form", form);
    erro.hidden = true;
    try {
      await api("/api/app/salario/portabilidade", { metodo: "POST", corpo: {
        bancoOrigem: form.banco.value, empregador: form.empregador.value, cnpj: form.cnpj.value,
        salario: lerValor(form.salario.value), diaPagamento: Number(form.dia.value) } });
      avisar("Pedido enviado. Acompanhe aqui: em cerca de 3 dias o salário passa a cair no Cofre.");
      ctx.recarregar();
    } catch (falha) {
      erro.textContent = falha.message;
      erro.hidden = false;
    }
  });

  const reserva = $("[data-reserva]", principal);
  if (reserva) {
    const atualizar = () => {
      const pct = Number(reserva.percentual.value);
      $("[data-pct]", reserva).textContent = pct + "%";
      $("[data-guardado]", reserva).textContent = moeda(salarioBase * pct / 100);
      $("[data-ano]", reserva).textContent = `${moeda(salarioBase * pct / 100 * 12)} em um ano, sem contar o rendimento`;
      reserva.percentual.style.setProperty("--preenchido", `${pct * 2}%`);
    };
    reserva.percentual.addEventListener("input", atualizar);
    atualizar();
    reserva.addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        await api("/api/app/salario/reserva", { metodo: "PUT", corpo: { percentual: Number(reserva.percentual.value) } });
        avisar(Number(reserva.percentual.value) ? "Pague-se primeiro ligado." : "Pague-se primeiro desligado.");
        ctx.recarregar();
      } catch (erro) {
        avisarErro(erro);
      }
    });
  }
}
