// Balcão da agência (caixa e gerente): busca de contas, depósito e saque em espécie, extrato.
import { api } from "./api.js";
import { $, $$, abrirModal, avisar, avisarErro, escapar, fecharModal, icone, lerValor, listaDeLancamentos, moeda } from "./ui.js";

export function linhaDeConta(c) {
  return `<tr class="clicavel" data-numero="${c.numero}" tabindex="0">
      <td class="mono">${c.numero}${c.tipo === "POUPANCA" ? ` <span class="etiqueta">poupança</span>` : ""}</td><td>${escapar(c.titular)}</td><td class="mono suave">${escapar(c.cpfMascarado)}</td>
      <td class="direita ${Number(c.saldo) < 0 ? "saida" : ""}">${moeda(c.saldo)}</td><td class="direita">${c.tipo === "POUPANCA" ? "—" : moeda(c.limite)}</td>
      <td><span class="situacao ${c.situacao}">${c.situacao.toLowerCase()}</span></td></tr>`;
}

export function tabelaDeContas(contas) {
  if (!contas.length) return `<div class="vazio">${icone("contas")}<span>Nenhuma conta encontrada.</span></div>`;
  return `<div class="tabela-rolagem"><table><thead><tr><th>Conta</th><th>Titular</th><th>CPF</th>
      <th class="direita">Saldo</th><th class="direita">Limite</th><th>Situação</th></tr></thead>
      <tbody>${contas.map(linhaDeConta).join("")}</tbody></table></div>`;
}

export function aoEscolherConta(raiz, funcao) {
  $$("tr[data-numero]", raiz).forEach((tr) => {
    tr.addEventListener("click", () => funcao(tr.dataset.numero));
    tr.addEventListener("keydown", (e) => { if (e.key === "Enter") funcao(tr.dataset.numero); });
  });
}

export async function balcao(principal) {
  principal.innerHTML = `<div class="topo"><div><h1>Balcão</h1><p>Depósitos e saques em espécie. Busque por nome, CPF ou número da conta.</p></div></div>
    <div class="grade-2">
      <section class="bloco grade">
        <form class="linha-campos" data-busca><label class="campo"><span>Buscar</span><input name="busca" placeholder="Ex.: Mario, 529.982 ou 10002-1" autocomplete="off"></label>
          <div class="acoes"><button class="botao" type="submit">Buscar</button></div></form>
        <div data-resultado></div>
      </section>
      <section class="bloco" data-detalhe><div class="vazio">${icone("balcao")}<span>Escolha uma conta para atender.</span></div></section>
    </div>`;
  const resultado = $("[data-resultado]", principal);
  const detalhe = $("[data-detalhe]", principal);

  const buscar = async (termo = "") => {
    const contas = await api("/api/agencia/contas?busca=" + encodeURIComponent(termo));
    resultado.innerHTML = tabelaDeContas(contas);
    aoEscolherConta(resultado, (n) => atender(n).catch(avisarErro));
  };

  const atender = async (numero) => {
    const [c, extrato] = await Promise.all([api("/api/agencia/contas/" + numero), api(`/api/agencia/contas/${numero}/extrato`)]);
    detalhe.innerHTML = `<div class="bloco-topo"><div><h2>${escapar(c.titular)}</h2><span class="suave">Ag. ${c.agencia} · Conta ${c.numero} · CPF ${escapar(c.cpfMascarado)}</span></div>
        <span class="situacao ${c.situacao}">${c.situacao.toLowerCase()}</span></div>
      ${c.motivoBloqueio ? `<p class="saida">Bloqueio: ${escapar(c.motivoBloqueio)}</p>` : ""}
      <div class="grade-3">
        <div class="kpi"><small>Saldo</small><strong class="${Number(c.saldo) < 0 ? "saida" : ""}">${moeda(c.saldo)}</strong></div>
        <div class="kpi"><small>Limite</small><strong>${moeda(c.limite)}</strong></div>
        <div class="kpi"><small>Disponível</small><strong>${moeda(c.disponivel)}</strong></div>
      </div>
      <div class="acoes espaco-topo"><button class="botao" data-operacao="saques">${icone("saque")} Saque</button>
        <button class="botao primario" data-operacao="depositos">${icone("deposito")} Depósito</button></div>
      <h3 class="espaco-topo">Últimos 30 dias</h3>
      <div class="lista">${listaDeLancamentos([...extrato.lancamentos].reverse())}</div>`;
    $$("[data-operacao]", detalhe).forEach((b) => b.addEventListener("click", () => operar(c, b.dataset.operacao)));
  };

  const operar = (conta, tipo) => {
    const nome = tipo === "depositos" ? "Depósito em espécie" : "Saque em espécie";
    const corpo = abrirModal(nome, `<form class="formulario" novalidate>
        <p class="suave">${escapar(conta.titular)} · Conta ${conta.numero}</p>
        <label class="campo valor-grande"><span>Valor</span><input name="valor" inputmode="decimal" placeholder="0,00"></label>
        ${tipo === "saques" ? `<small class="dica">Disponível (com cheque especial): ${moeda(conta.disponivel)}</small>` : ""}
        <p class="erro-form" hidden></p><div class="acoes"><button class="botao primario" type="submit">Confirmar</button></div></form>`);
    const form = $("form", corpo);
    form.valor.focus();
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const erro = $(".erro-form", form);
      const valor = lerValor(form.valor.value);
      if (!(valor > 0)) {
        erro.textContent = "Digite um valor maior que zero.";
        erro.hidden = false;
        return;
      }
      try {
        const atualizada = await api(`/api/agencia/contas/${conta.numero}/${tipo}`, { metodo: "POST", corpo: { valor } });
        fecharModal();
        avisar(`${nome} de ${moeda(valor)}. Novo saldo: ${moeda(atualizada.saldo)}.`, { titulo: "Operação concluída" });
        await atender(conta.numero);
        await buscar($("[data-busca]", principal).busca.value);
      } catch (falha) {
        erro.textContent = falha.message;
        erro.hidden = false;
      }
    });
  };

  $("[data-busca]", principal).addEventListener("submit", (e) => {
    e.preventDefault();
    buscar(e.target.busca.value).catch(avisarErro);
  });
  await buscar();
}
