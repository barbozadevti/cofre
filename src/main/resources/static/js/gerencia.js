// Backoffice do gerente: indicadores, contas (limite, bloqueio, encerramento, senha), nova conta e auditoria.
import { api } from "./api.js";
import { $, abrirModal, avisar, avisarErro, confirmar, dataHora, escapar, fecharModal, icone, lerValor, moeda } from "./ui.js";
import { aoEscolherConta, tabelaDeContas } from "./agencia.js";

const ACOES = {
  LOGIN: "Entrou", LOGIN_FALHOU: "Senha errada", USUARIO_BLOQUEADO: "Acesso bloqueado", CONTA_ABERTA: "Conta aberta",
  LIMITE_ALTERADO: "Limite alterado", CONTA_BLOQUEADA: "Conta bloqueada", CONTA_DESBLOQUEADA: "Conta desbloqueada",
  CONTA_ENCERRADA: "Conta encerrada", SENHA_REDEFINIDA: "Senha redefinida", SENHA_ALTERADA: "Senha alterada",
  PIX_ENVIADO: "Pix enviado", PIX_CHAVE_CRIADA: "Chave Pix criada", PIX_CHAVE_EXCLUIDA: "Chave Pix excluída",
  TRANSFERENCIA: "Transferência", DEPOSITO_ESPECIE: "Depósito em espécie", SAQUE_ESPECIE: "Saque em espécie",
  CAIXINHA_GUARDAR: "Guardou na caixinha", CAIXINHA_RESGATAR: "Resgatou da caixinha", CARTAO_DADOS_VISTOS: "Viu dados do cartão",
  CARTAO_BLOQUEADO: "Bloqueou o cartão", CARTAO_DESBLOQUEADO: "Desbloqueou o cartão",
};

export async function painel(principal) {
  const [i, eventos] = await Promise.all([api("/api/gerencia/indicadores"), api("/api/gerencia/auditoria")]);
  principal.innerHTML = `<div class="topo"><div><h1>Painel da agência</h1><p>Visão geral em tempo real.</p></div>
      <a class="botao primario" href="#/nova-conta">${icone("novo")} Abrir conta</a></div>
    <div class="grade-4">
      <div class="kpi"><small>Clientes</small><strong>${i.clientes}</strong></div>
      <div class="kpi"><small>Contas ativas</small><strong>${i.contasAtivas}</strong><small>${i.contasBloqueadas} bloqueada(s)</small></div>
      <div class="kpi"><small>Saldo em custódia</small><strong>${moeda(i.emCustodia)}</strong></div>
      <div class="kpi"><small>Em caixinhas</small><strong>${moeda(i.emCaixinhas)}</strong></div>
      <div class="kpi"><small>Cheque especial concedido</small><strong>${moeda(i.limiteConcedido)}</strong></div>
      <div class="kpi"><small>Cheque especial em uso</small><strong class="${Number(i.limiteEmUso) > 0 ? "saida" : ""}">${moeda(i.limiteEmUso)}</strong></div>
      <div class="kpi"><small>Pix enviados hoje</small><strong>${i.pixHoje}</strong></div>
      <div class="kpi"><small>Volume de Pix hoje</small><strong>${moeda(i.volumePixHoje)}</strong></div>
    </div>
    <section class="bloco espaco-topo"><div class="bloco-topo"><h2>Atividade recente</h2><a class="botao fantasma pequeno" href="#/auditoria">Ver auditoria</a></div>
      ${tabelaDeEventos(eventos.eventos.slice(0, 8))}</section>`;
}

function tabelaDeEventos(eventos) {
  if (!eventos.length) return `<div class="vazio">${icone("auditoria")}<span>Nenhum evento ainda.</span></div>`;
  return `<div class="tabela-rolagem"><table><thead><tr><th>Quando</th><th>Quem</th><th>Ação</th><th>Detalhe</th><th>Origem</th></tr></thead>
      <tbody>${eventos.map((e) => `<tr><td class="suave">${dataHora(e.dataHora)}</td>
        <td>${escapar(e.usuario)}${e.perfil ? ` <span class="suave">· ${escapar(e.perfil.toLowerCase())}</span>` : ""}</td>
        <td>${escapar(ACOES[e.acao] || e.acao)}</td><td class="suave">${escapar(e.detalhe || "")}</td>
        <td class="mono suave">${escapar(e.origem || "")}</td></tr>`).join("")}</tbody></table></div>`;
}

export async function contas(principal) {
  principal.innerHTML = `<div class="topo"><div><h1>Contas</h1><p>Clique numa conta para ajustar limite, bloquear, encerrar ou redefinir a senha.</p></div>
      <a class="botao primario" href="#/nova-conta">${icone("novo")} Abrir conta</a></div>
    <section class="bloco grade">
      <form class="linha-campos" data-busca><label class="campo"><span>Buscar</span><input name="busca" placeholder="Nome, CPF ou número" autocomplete="off"></label>
        <div class="acoes"><button class="botao" type="submit">Buscar</button></div></form>
      <div data-resultado></div></section>`;
  const resultado = $("[data-resultado]", principal);
  const buscar = async (termo = "") => {
    resultado.innerHTML = tabelaDeContas(await api("/api/agencia/contas?busca=" + encodeURIComponent(termo)));
    aoEscolherConta(resultado, (n) => gerir(n, () => buscar($("[data-busca]", principal).busca.value)).catch(avisarErro));
  };
  $("[data-busca]", principal).addEventListener("submit", (e) => {
    e.preventDefault();
    buscar(e.target.busca.value).catch(avisarErro);
  });
  await buscar();
}

async function gerir(numero, atualizarLista) {
  const c = await api("/api/agencia/contas/" + numero);
  const ativa = c.situacao !== "ENCERRADA";
  const corpo = abrirModal(c.titular, `<div class="formulario">
      <p class="suave">${c.tipoNome} · Ag. ${c.agencia} · Conta ${c.numero} · CPF ${escapar(c.cpfMascarado)} · <span class="situacao ${c.situacao}">${c.situacao.toLowerCase()}</span></p>
      <div class="grade-3"><div class="kpi"><small>Saldo</small><strong class="${Number(c.saldo) < 0 ? "saida" : ""}">${moeda(c.saldo)}</strong></div>
        ${c.tipo === "POUPANCA"
          ? `<div class="kpi"><small>Próximo rendimento</small><strong>${moeda(c.rendimentoEstimado)}</strong></div>
             <div class="kpi"><small>Aniversário</small><strong>dia ${c.diaDeAniversario}</strong></div></div>`
          : `<div class="kpi"><small>Limite</small><strong>${moeda(c.limite)}</strong></div>
             <div class="kpi"><small>Em uso</small><strong>${moeda(c.usoDoLimite)}</strong></div></div>`}
      ${c.motivoBloqueio ? `<p class="saida">Motivo do bloqueio: ${escapar(c.motivoBloqueio)}</p>` : ""}
      ${ativa ? `${c.tipo === "POUPANCA" ? "" : `<form class="linha-campos" data-limite><label class="campo"><span>Limite do cheque especial</span>
          <input name="limite" inputmode="decimal" value="${Number(c.limite).toLocaleString("pt-BR", { minimumFractionDigits: 2 })}"></label>
          <div class="acoes"><button class="botao primario" type="submit">Salvar limite</button></div></form>`}
        <div class="acoes">
          ${c.situacao === "BLOQUEADA" ? `<button class="botao" data-desbloquear>${icone("cadeado")} Desbloquear</button>`
            : `<button class="botao" data-bloquear>${icone("cadeado")} Bloquear</button>`}
          <button class="botao" data-senha>${icone("perfil")} Redefinir senha</button>
          <button class="botao perigo" data-encerrar>${icone("lixo")} Encerrar</button></div>` : `<p class="suave">Conta encerrada em ${dataHora(c.encerradaEm)}.</p>`}
      <p class="erro-form" hidden></p></div>`);
  const erro = $(".erro-form", corpo);
  const executar = async (acao, sucesso) => {
    erro.hidden = true;
    try {
      const retorno = await acao();
      await atualizarLista();
      if (sucesso) sucesso(retorno);
    } catch (falha) {
      erro.textContent = falha.message;
      erro.hidden = false;
    }
  };
  if (!ativa) return;
  $("[data-limite]", corpo)?.addEventListener("submit", (e) => {
    e.preventDefault();
    const limite = lerValor(e.target.limite.value);
    if (Number.isNaN(limite)) {
      erro.textContent = "Limite inválido.";
      erro.hidden = false;
      return;
    }
    executar(() => api(`/api/gerencia/contas/${numero}/limite`, { metodo: "PUT", corpo: { limite } }), () => {
      avisar(`Limite de ${moeda(limite)} definido.`, { titulo: c.titular });
      gerir(numero, atualizarLista);
    });
  });
  const bloquear = $("[data-bloquear]", corpo);
  if (bloquear) bloquear.addEventListener("click", () => {
    const form = abrirModal("Bloquear conta " + numero, `<form class="formulario" novalidate>
        <p class="suave">A conta continua recebendo, mas nenhuma saída (Pix, transferência, saque) é aceita até o desbloqueio.</p>
        <label class="campo"><span>Motivo (fica na auditoria)</span><input name="motivo" value="Suspeita de fraude" maxlength="200"></label>
        <p class="erro-form" hidden></p><div class="acoes"><button class="botao perigo" type="submit">Bloquear</button></div></form>`).querySelector("form");
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        await api(`/api/gerencia/contas/${numero}/bloqueio`, { metodo: "POST", corpo: { motivo: form.motivo.value } });
        await atualizarLista();
        gerir(numero, atualizarLista);
      } catch (falha) {
        const erroBloqueio = form.querySelector(".erro-form");
        erroBloqueio.textContent = falha.message;
        erroBloqueio.hidden = false;
      }
    });
  });
  const desbloquear = $("[data-desbloquear]", corpo);
  if (desbloquear) desbloquear.addEventListener("click", () =>
    executar(() => api(`/api/gerencia/contas/${numero}/bloqueio`, { metodo: "DELETE" }), () => gerir(numero, atualizarLista)));
  $("[data-senha]", corpo).addEventListener("click", () =>
    executar(() => api(`/api/gerencia/contas/${numero}/senha`, { metodo: "POST" }), (r) => {
      abrirModal("Nova senha provisória", `<div class="formulario"><p class="suave">Entregue ao cliente. Ela aparece só agora e precisa ser trocada no primeiro acesso.</p>
        <div class="senha-provisoria">${escapar(r.senhaProvisoria)}</div></div>`);
    }));
  $("[data-encerrar]", corpo).addEventListener("click", async () => {
    if (!(await confirmar("Encerrar conta " + numero, "Exige saldo e caixinhas zerados. As chaves Pix são removidas e o cartão é bloqueado.", { rotulo: "Encerrar", perigo: true }))) {
      return gerir(numero, atualizarLista);
    }
    try {
      await api("/api/gerencia/contas/" + numero, { metodo: "DELETE" });
      avisar("Conta encerrada.", { titulo: numero });
      await atualizarLista();
    } catch (falha) {
      avisarErro(falha);
    }
  });
}

export async function novaConta(principal) {
  principal.innerHTML = `<div class="topo"><div><h1>Abrir conta</h1><p>Cliente novo recebe acesso ao app com senha provisória. CPF já cadastrado ganha uma conta adicional.</p></div></div>
    <section class="bloco"><form class="formulario" novalidate>
      <div class="linha-campos">
        <label class="campo"><span>Nome completo</span><input name="nome" autocomplete="off"></label>
        <label class="campo"><span>CPF</span><input name="cpf" inputmode="numeric" placeholder="000.000.000-00" autocomplete="off"></label>
      </div>
      <div class="linha-campos">
        <label class="campo"><span>E-mail</span><input name="email" type="email" autocomplete="off"></label>
        <label class="campo"><span>Celular (opcional)</span><input name="telefone" inputmode="tel" placeholder="(11) 98765-4321"></label>
      </div>
      <div class="linha-campos">
        <label class="campo"><span>Agência</span><input name="agencia" inputmode="numeric" maxlength="4" placeholder="0001"></label>
        <label class="campo"><span>Depósito inicial</span><input name="deposito" inputmode="decimal" placeholder="0,00"></label>
        <label class="campo"><span>Limite do cheque especial</span><input name="limite" inputmode="decimal" placeholder="0,00"></label>
      </div>
      <p class="erro-form" hidden></p>
      <div class="acoes"><button class="botao primario" type="submit">${icone("novo")} Abrir conta</button></div>
    </form></section>`;
  const form = $("form", principal);
  form.nome.focus();
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const erro = $(".erro-form", form);
    erro.hidden = true;
    const deposito = form.deposito.value.trim() ? lerValor(form.deposito.value) : 0;
    const limite = form.limite.value.trim() ? lerValor(form.limite.value) : 0;
    if (Number.isNaN(deposito) || Number.isNaN(limite)) {
      erro.textContent = "Confira o depósito inicial e o limite (ex.: 1.500,00).";
      erro.hidden = false;
      return;
    }
    try {
      const r = await api("/api/gerencia/contas", {
        metodo: "POST",
        corpo: { nome: form.nome.value, cpf: form.cpf.value, email: form.email.value, telefone: form.telefone.value,
          agencia: form.agencia.value, depositoInicial: deposito, limite },
      });
      form.reset();
      abrirModal("Conta aberta", `<div class="formulario"><p>${escapar(r.mensagem)}</p>
        ${r.senhaProvisoria ? `<span class="rotulo">Senha provisória do app</span><div class="senha-provisoria">${escapar(r.senhaProvisoria)}</div>
          <p class="suave">Aparece só agora. O cliente entra com o e-mail ou CPF e cria uma senha própria no primeiro acesso.</p>`
          : `<p class="suave">O CPF já era cliente: a conta nova aparece no app dele, com a mesma senha.</p>`}
        <div class="acoes"><a class="botao" href="#/contas" data-fechar-ir>Ver contas</a></div></div>`);
      $("[data-fechar-ir]").addEventListener("click", fecharModal);
    } catch (falha) {
      erro.textContent = falha.message;
      erro.hidden = false;
    }
  });
}

export async function auditoria(principal) {
  const pagina = Number(new URLSearchParams(location.hash.split("?")[1] || "").get("pagina") || 0);
  const p = await api("/api/gerencia/auditoria?pagina=" + pagina);
  principal.innerHTML = `<div class="topo"><div><h1>Auditoria</h1><p>Quem fez o quê, quando e de onde. Registros não podem ser alterados.</p></div></div>
    <section class="bloco">${tabelaDeEventos(p.eventos)}
      <div class="acoes espaco-topo">
        ${pagina > 0 ? `<a class="botao pequeno" href="#/auditoria?pagina=${pagina - 1}">Mais recentes</a>` : ""}
        <span class="suave">Página ${pagina + 1} de ${Math.max(1, p.totalDePaginas)}</span>
        ${pagina + 1 < p.totalDePaginas ? `<a class="botao pequeno" href="#/auditoria?pagina=${pagina + 1}">Mais antigos</a>` : ""}
      </div></section>`;
}
