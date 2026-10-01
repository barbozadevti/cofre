// Telas do cliente: início, Pix, extrato, caixinhas e cartão virtual.
import { api, novaChaveDeIdempotencia } from "./api.js";
import {
  $, $$, abrirModal, avisar, avisarErro, confirmar, escapar, fecharModal, graficoMensal, hojeISO, icone, iniciais,
  lerValor, listaDeLancamentos, moeda, mostrarComprovante,
} from "./ui.js";
import { botaoPrivacidade } from "./app.js";

let contaAtual = null;

async function minhasContas() {
  const contas = await api("/api/app/contas");
  if (!contas.length) throw new Error("Você ainda não tem conta aberta.");
  if (!contas.some((c) => c.numero === contaAtual)) contaAtual = contas[0].numero;
  return contas;
}

function parametro(nome) {
  return new URLSearchParams(location.hash.split("?")[1] || "").get(nome);
}

function seletorDeConta(contas) {
  if (contas.length < 2) return "";
  return `<label class="campo"><span>Conta</span><select data-conta>${contas.map((c) =>
    `<option value="${c.numero}" ${c.numero === contaAtual ? "selected" : ""}>${c.tipo === "POUPANCA" ? "Poupança" : "Corrente"} · ${c.numero}</option>`).join("")}</select></label>`;
}

function ligarSeletorDeConta(raiz, recarregar) {
  const s = $("[data-conta]", raiz);
  if (s) s.addEventListener("change", () => { contaAtual = s.value; recarregar(); });
}

function ligarComprovantes(raiz) {
  raiz.addEventListener("click", async (e) => {
    const alvo = e.target.closest("[data-comprovante]");
    if (!alvo) return;
    try {
      mostrarComprovante(await api("/api/app/comprovantes/" + encodeURIComponent(alvo.dataset.comprovante)));
    } catch (erro) {
      avisarErro(erro);
    }
  });
}

// ---------- Início ----------

export async function inicio(principal, ctx) {
  const painel = await api("/api/app/painel");
  const contas = await minhasContas();
  const conta = painel.contas.find((c) => c.numero === contaAtual) || painel.contas[0];
  const corrente = contas.find((c) => c.tipo === "CORRENTE");
  const poupanca = contas.find((c) => c.tipo === "POUPANCA");
  const ehPoupanca = conta.tipo === "POUPANCA";
  const ultimos = await api(`/api/app/contas/${conta.numero}/ultimos`);
  const primeiroNome = painel.nome.split(" ")[0];
  const uso = Number(conta.usoDoLimite);
  const limite = Number(conta.limite);
  const hora = Number(new Intl.DateTimeFormat("pt-BR", { hour: "numeric", hourCycle: "h23", timeZone: "America/Sao_Paulo" }).format(new Date()));
  const saudacao = hora < 5 ? "Boa noite" : hora < 12 ? "Bom dia" : hora < 18 ? "Boa tarde" : "Boa noite";

  principal.innerHTML = `
    <div class="topo"><div><h1>${saudacao}, ${escapar(primeiroNome)}</h1><p>${conta.tipoNome} · Ag. ${conta.agencia} · Conta ${conta.numero}</p></div>
      <div class="topo-acoes">${seletorDeConta(contas)}${botaoPrivacidade()}</div></div>
    <div class="grade-2">
      <div class="grade">
        <section class="saldo-principal">
          <span class="rotulo">${ehPoupanca ? "Saldo na poupança" : "Saldo em conta"}</span>
          <div class="valor sensivel ${Number(conta.saldo) < 0 ? "saida" : ""}">${moeda(conta.saldo)}</div>
          <span class="suave">Disponível para usar: <span class="sensivel">${moeda(conta.disponivel)}</span></span>
          <div class="detalhes">
            <span>Guardado em caixinhas<strong class="sensivel">${moeda(painel.emCaixinhas)}</strong></span>
            ${ehPoupanca
              ? `<span>Próximo rendimento<strong class="sensivel">${moeda(conta.rendimentoEstimado)}</strong></span>`
              : `<span>Cheque especial<strong class="sensivel">${moeda(limite)}</strong></span>`}
          </div>
        </section>
        <nav class="atalhos" aria-label="Atalhos">
          <a class="atalho" href="#/pix?aba=enviar"><span class="circulo">${icone("pix")}</span>Pix</a>
          <a class="atalho" href="#/pix?aba=cobrar"><span class="circulo">${icone("qr")}</span>Cobrar</a>
          <a class="atalho" href="#/pix?aba=colar"><span class="circulo">${icone("colar")}</span>Copia e cola</a>
          <button class="atalho" type="button" data-transferir><span class="circulo">${icone("transferir")}</span>Transferir</button>
          <a class="atalho" href="#/caixinhas"><span class="circulo">${icone("caixinha")}</span>Caixinhas</a>
          ${ehPoupanca ? "" : `<a class="atalho" href="#/cartao"><span class="circulo">${icone("cartao")}</span>Cartão</a>`}
        </nav>
        <section class="bloco">
          <div class="bloco-topo"><h2>Entradas e saídas</h2>
            <div class="legenda"><span><i class="l-entrada"></i>Entrou</span><span><i class="l-saida"></i>Saiu</span></div></div>
          <div class="grafico sensivel">${graficoMensal(painel.meses)}</div>
        </section>
      </div>
      <div class="grade">
        ${blocoPoupanca(conta, corrente, poupanca)}
        <section class="bloco" ${ehPoupanca ? "hidden" : ""}>
          <div class="bloco-topo"><h2>Cheque especial</h2><span class="suave sensivel">${moeda(uso)} de ${moeda(limite)}</span></div>
          <div class="medidor ${uso > 0 ? "alerta" : ""}" data-medidor="${limite > 0 ? Math.min(100, (uso / limite) * 100) : 0}"><span></span></div>
          <p class="suave espaco-topo">${uso > 0
            ? `Você está usando o limite. Juros de 8% ao mês, cobrados por dia sobre o saldo negativo.`
            : limite > 0 ? `Limite disponível para emergências. Só é cobrado se você usar.` : `Sem limite de cheque especial. Fale com seu gerente.`}</p>
        </section>
        <section class="bloco">
          <div class="bloco-topo"><h2>Últimas movimentações</h2><a href="#/extrato" class="botao fantasma pequeno">Ver extrato</a></div>
          <div class="lista" data-lista>${listaDeLancamentos(ultimos)}</div>
        </section>
      </div>
    </div>`;
  $$("[data-medidor]", principal).forEach((m) => { $("span", m).style.width = m.dataset.medidor + "%"; });
  ligarComprovantes(principal);
  ligarSeletorDeConta(principal, ctx.recarregar);
  $("[data-transferir]", principal).addEventListener("click", () => transferir(ctx));
  $("[data-abrir-poupanca]", principal)?.addEventListener("click", async (e) => {
    e.currentTarget.disabled = true;
    try {
      const nova = await api("/api/app/poupanca", { metodo: "POST" });
      contaAtual = nova.numero;
      avisar(`Poupança ${nova.numero} aberta. Ela rende 0,5% ao mês, todo dia ${nova.diaDeAniversario}.`);
      ctx.recarregar();
    } catch (erro) {
      avisarErro(erro);
      e.currentTarget.disabled = false;
    }
  });
  $$("[data-mover]", principal).forEach((b) => b.addEventListener("click", () => {
    const [origem, destino] = b.dataset.mover.split(">");
    moverEntreContas(origem, destino, b.dataset.titulo, ctx);
  }));
  $("[data-ver-poupanca]", principal)?.addEventListener("click", () => { contaAtual = poupanca.numero; ctx.recarregar(); });
}

/** Na corrente: convite para abrir a poupança ou atalho para guardar. Na poupança: rendimento e resgate. */
function blocoPoupanca(conta, corrente, poupanca) {
  if (!corrente) return "";
  if (!poupanca) {
    return `<section class="bloco destaque-poupanca">
      <div class="bloco-topo"><h2>Poupança</h2><span class="etiqueta">0,5% ao mês</span></div>
      <p class="suave">Separe uma reserva que rende todo mês, no dia em que a conta foi aberta. Sem tarifa e com resgate na hora.</p>
      <div class="acoes espaco-topo"><button class="botao primario" type="button" data-abrir-poupanca>${icone("novo")} Abrir minha poupança</button></div>
    </section>`;
  }
  if (conta.tipo === "POUPANCA") {
    return `<section class="bloco destaque-poupanca">
      <div class="bloco-topo"><h2>Rendimento</h2><span class="etiqueta">todo dia ${conta.diaDeAniversario}</span></div>
      <p class="sensivel"><strong class="valor-medio">${moeda(conta.rendimentoEstimado)}</strong> <span class="suave">previstos no próximo aniversário</span></p>
      <p class="suave espaco-topo">A poupança rende 0,5% ao mês sobre o saldo e não tem cheque especial: o disponível é só o saldo.</p>
      <div class="acoes espaco-topo">
        <button class="botao primario" type="button" data-mover="${corrente.numero}>${conta.numero}" data-titulo="Guardar na poupança">${icone("caixinha")} Guardar</button>
        <button class="botao" type="button" data-mover="${conta.numero}>${corrente.numero}" data-titulo="Resgatar para a conta corrente">${icone("transferir")} Resgatar</button>
      </div>
    </section>`;
  }
  return `<section class="bloco destaque-poupanca">
    <div class="bloco-topo"><h2>Sua poupança</h2><span class="etiqueta">rende dia ${poupanca.diaDeAniversario}</span></div>
    <p class="sensivel"><strong class="valor-medio">${moeda(poupanca.saldo)}</strong> <span class="suave">· próximo rendimento ${moeda(poupanca.rendimentoEstimado)}</span></p>
    <div class="acoes espaco-topo">
      <button class="botao primario" type="button" data-mover="${conta.numero}>${poupanca.numero}" data-titulo="Guardar na poupança">${icone("caixinha")} Guardar</button>
      <button class="botao fantasma" type="button" data-ver-poupanca>Ver poupança</button>
    </div>
  </section>`;
}

/** Guardar na poupança ou resgatar: uma transferência entre as próprias contas. */
function moverEntreContas(origem, destino, titulo, ctx) {
  const chave = novaChaveDeIdempotencia();
  const corpo = abrirModal(titulo, `<form class="formulario" novalidate>
      <label class="campo valor-grande"><span>Valor</span><input name="valor" inputmode="decimal" placeholder="0,00" autocomplete="off" autofocus></label>
      <p class="erro-form" hidden></p>
      <div class="acoes"><button class="botao primario" type="submit">Confirmar</button></div></form>`);
  const form = $("form", corpo);
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const erro = $(".erro-form", form);
    erro.hidden = true;
    const valor = lerValor(form.valor.value);
    if (!(valor > 0)) {
      erro.textContent = "Digite um valor maior que zero, por exemplo 150,75.";
      erro.hidden = false;
      return;
    }
    try {
      await api("/api/app/transferencias", { metodo: "POST", cabecalhos: { "Idempotency-Key": chave },
        corpo: { origem, destino, valor, mensagem: titulo } });
      fecharModal();
      avisar(`${titulo}: ${moeda(valor)}.`);
      ctx.recarregar();
    } catch (falha) {
      erro.textContent = falha.message;
      erro.hidden = false;
    }
  });
}

/** Transferência entre contas do Cofre, pelo número da conta. */
function transferir(ctx) {
  const chave = novaChaveDeIdempotencia();
  const corpo = abrirModal("Transferir", `<form class="formulario" novalidate>
      <label class="campo"><span>Conta de destino</span><input name="destino" placeholder="10001-3" autocomplete="off" required></label>
      <label class="campo valor-grande"><span>Valor</span><input name="valor" inputmode="decimal" placeholder="0,00" autocomplete="off"></label>
      <label class="campo"><span>Mensagem (opcional)</span><input name="mensagem" maxlength="140"></label>
      <p class="erro-form" hidden></p>
      <div class="acoes"><button class="botao primario" type="submit">Transferir</button></div></form>`);
  const form = $("form", corpo);
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const erro = $(".erro-form", form);
    erro.hidden = true;
    const valor = lerValor(form.valor.value);
    if (!(valor > 0)) {
      erro.textContent = "Digite um valor maior que zero, por exemplo 150,75.";
      erro.hidden = false;
      return;
    }
    try {
      const comprovante = await api("/api/app/transferencias", {
        metodo: "POST", cabecalhos: { "Idempotency-Key": chave },
        corpo: { origem: contaAtual, destino: form.destino.value.trim(), valor, mensagem: form.mensagem.value },
      });
      mostrarComprovante(comprovante);
      ctx.recarregar();
    } catch (falha) {
      erro.textContent = falha.message;
      erro.hidden = false;
    }
  });
}

// ---------- Pix ----------

export async function pix(principal, ctx) {
  const contas = await minhasContas();
  const aba = parametro("aba") || "enviar";
  const abas = [["enviar", "Enviar"], ["cobrar", "Cobrar"], ["colar", "Copia e cola"], ["chaves", "Minhas chaves"], ["limites", "Limites"]];
  principal.innerHTML = `<div class="topo"><div><h1>Pix</h1><p>Transferências instantâneas, 24 horas por dia.</p></div>
      <div class="topo-acoes">${seletorDeConta(contas)}</div></div>
    <div class="abas" role="tablist">${abas.map(([id, nome]) =>
      `<a class="aba" role="tab" href="#/pix?aba=${id}" aria-selected="${id === aba}">${nome}</a>`).join("")}</div>
    <div data-conteudo></div>`;
  ligarSeletorDeConta(principal, ctx.recarregar);
  const conteudo = $("[data-conteudo]", principal);
  const telas = { enviar: pixEnviar, cobrar: pixCobrar, colar: pixColar, chaves: pixChaves, limites: pixLimites };
  await (telas[aba] || pixEnviar)(conteudo, ctx);
}

function cartaoDestinatario(d) {
  return `<div class="destinatario"><span class="avatar">${iniciais(d.nome)}</span>
      <div><strong>${escapar(d.nome)}</strong><div class="suave">CPF ${escapar(d.cpfMascarado)} · ${escapar(d.instituicao)}</div>
      <div class="suave">Ag. ${escapar(d.agencia)} · Conta ${escapar(d.conta)}</div></div></div>`;
}

async function pixEnviar(raiz, ctx) {
  const limites = await api(`/api/pix/contas/${contaAtual}/limites`);
  raiz.innerHTML = `<div class="grade-2"><section class="bloco" data-passo></section>
      <aside class="bloco grade">
        <div><span class="rotulo">${limites.noturno ? "Período noturno (20h às 6h)" : "Período diurno (6h às 20h)"}</span>
          <p class="sensivel"><strong>${moeda(limites.disponivelNoPeriodo)}</strong> <span class="suave">disponíveis de ${moeda(limites.limitePeriodo)}</span></p></div>
        <div class="medidor" data-uso="${(Number(limites.usadoNoPeriodo) / Number(limites.limitePeriodo)) * 100}"><span></span></div>
        <p class="suave">${icone("escudo")} Antes de confirmar, o Cofre mostra o nome e o CPF mascarado de quem vai receber. Confira sempre.</p>
      </aside></div>`;
  const medidor = $("[data-uso]", raiz);
  $("span", medidor).style.width = Math.min(100, Number(medidor.dataset.uso)) + "%";
  const passo = $("[data-passo]", raiz);

  const passo1 = () => {
    passo.innerHTML = `<form class="formulario" novalidate>
        <div class="bloco-topo"><h2>Para quem?</h2></div>
        <label class="campo"><span>Chave Pix</span><input name="chave" placeholder="CPF, e-mail, celular ou chave aleatória" autocomplete="off" required>
          <small class="dica">Experimente: ana@cofre.dev ou mercado@cofre.dev</small></label>
        <p class="erro-form" hidden></p>
        <div class="acoes"><button class="botao primario" type="submit">Continuar</button></div></form>`;
    const form = $("form", passo);
    form.chave.focus();
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        const destinatario = await api("/api/pix/destinatarios?chave=" + encodeURIComponent(form.chave.value.trim()));
        passo2(destinatario);
      } catch (falha) {
        const erro = $(".erro-form", form);
        erro.textContent = falha.status === 404 ? "Não encontramos essa chave Pix no Cofre." : falha.message;
        erro.hidden = false;
      }
    });
  };

  const passo2 = (destinatario) => {
    const chave = novaChaveDeIdempotencia();
    passo.innerHTML = `<form class="formulario" novalidate>
        <div class="bloco-topo"><h2>Quanto?</h2><button type="button" class="botao fantasma pequeno" data-voltar>Trocar destinatário</button></div>
        ${cartaoDestinatario(destinatario)}
        <label class="campo valor-grande"><span>Valor</span><input name="valor" inputmode="decimal" placeholder="0,00" autocomplete="off"></label>
        <label class="campo"><span>Mensagem (opcional)</span><input name="mensagem" maxlength="140" placeholder="Ex.: pizza de sexta"></label>
        <p class="erro-form" hidden></p>
        <div class="acoes"><button class="botao primario" type="submit">${icone("pix")} Enviar Pix</button></div></form>`;
    const form = $("form", passo);
    form.valor.focus();
    $("[data-voltar]", passo).addEventListener("click", passo1);
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const erro = $(".erro-form", form);
      erro.hidden = true;
      const valor = lerValor(form.valor.value);
      if (!(valor > 0)) {
        erro.textContent = "Digite um valor maior que zero, por exemplo 150,75.";
        erro.hidden = false;
        return;
      }
      const botao = form.querySelector("button[type=submit]");
      botao.disabled = true;
      try {
        const comprovante = await api("/api/pix/envios", {
          metodo: "POST", cabecalhos: { "Idempotency-Key": chave },
          corpo: { conta: contaAtual, chave: destinatario.chave, valor, mensagem: form.mensagem.value },
        });
        mostrarComprovante(comprovante);
        await pixEnviar(raiz, ctx);
      } catch (falha) {
        erro.textContent = falha.message;
        erro.hidden = false;
        botao.disabled = false;
      }
    });
  };
  passo1();
}

async function pixCobrar(raiz) {
  const chaves = await api(`/api/pix/contas/${contaAtual}/chaves`);
  if (!chaves.length) {
    raiz.innerHTML = `<section class="bloco vazio">${icone("qr")}<span>Cadastre uma chave Pix para poder receber.</span>
      <a class="botao primario" href="#/pix?aba=chaves">Cadastrar chave</a></section>`;
    return;
  }
  raiz.innerHTML = `<div class="grade-2">
      <section class="bloco"><form class="formulario" novalidate>
        <div class="bloco-topo"><h2>Cobrar com QR Code</h2></div>
        <label class="campo"><span>Receber na chave</span><select name="chave">${chaves.map((c) =>
          `<option value="${escapar(c.valor)}">${escapar(c.tipoNome)} · ${escapar(c.exibicao)}</option>`).join("")}</select></label>
        <label class="campo valor-grande"><span>Valor (opcional)</span><input name="valor" inputmode="decimal" placeholder="Quem paga escolhe"></label>
        <label class="campo"><span>Descrição (opcional)</span><input name="descricao" maxlength="40" placeholder="Ex.: aula de inglês"></label>
        <p class="erro-form" hidden></p>
        <div class="acoes"><button class="botao primario" type="submit">${icone("qr")} Gerar QR Code</button></div>
      </form></section>
      <section class="bloco grade" data-resultado><div class="vazio">${icone("qr")}<span>O QR Code aparece aqui.</span></div></section>
    </div>`;
  const form = $("form", raiz);
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const erro = $(".erro-form", form);
    erro.hidden = true;
    const texto = form.valor.value.trim();
    const valor = texto ? lerValor(texto) : null;
    if (texto && !(valor > 0)) {
      erro.textContent = "Valor inválido. Use, por exemplo, 150,75 (ou deixe em branco).";
      erro.hidden = false;
      return;
    }
    try {
      const c = await api("/api/pix/cobrancas", { metodo: "POST", corpo: { conta: contaAtual, chave: form.chave.value, valor, descricao: form.descricao.value } });
      const resultado = $("[data-resultado]", raiz);
      resultado.innerHTML = `<div class="qr">${c.qrCodeSvg}</div>
        <div class="grade"><span class="rotulo">Pix copia e cola</span><div class="copia" data-codigo>${escapar(c.copiaECola)}</div>
        <button type="button" class="botao" data-copiar>${icone("copiar")} Copiar código</button>
        <p class="suave">${c.valor ? `Valor fixo: <strong>${moeda(c.valor)}</strong>. ` : "Sem valor definido: quem paga escolhe. "}Padrão BR Code (EMV) com conferência CRC16.</p>
        <p class="alerta-demo">${icone("escudo")} Demonstração: o código segue o padrão real do Pix. Pague com outra conta do Cofre (aba Copia e cola), nunca com o app do seu banco.</p></div>`;
      $("[data-copiar]", resultado).addEventListener("click", async () => {
        try {
          await navigator.clipboard.writeText(c.copiaECola);
          avisar("Código copiado. Cole na aba Copia e cola de outra conta para testar.", { titulo: "Copiado" });
        } catch {
          avisar("Selecione o código e copie manualmente.", { erro: true });
        }
      });
    } catch (falha) {
      erro.textContent = falha.message;
      erro.hidden = false;
    }
  });
}

async function pixColar(raiz, ctx) {
  raiz.innerHTML = `<div class="grade-2"><section class="bloco"><form class="formulario" novalidate>
        <div class="bloco-topo"><h2>Pagar com Pix copia e cola</h2></div>
        <label class="campo"><span>Código</span><textarea name="codigo" placeholder="00020126..." spellcheck="false"></textarea></label>
        <p class="erro-form" hidden></p>
        <div class="acoes"><button class="botao primario" type="submit">Conferir código</button></div></form></section>
      <section class="bloco" data-confirmacao><div class="vazio">${icone("colar")}<span>Cole um código gerado na aba Cobrar (de outra conta) ou em qualquer app.</span></div></section></div>`;
  const form = $("form", raiz);
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const erro = $(".erro-form", form);
    erro.hidden = true;
    try {
      const leitura = await api("/api/pix/copia-e-cola/leitura", { metodo: "POST", corpo: { codigo: form.codigo.value } });
      const chave = novaChaveDeIdempotencia();
      const area = $("[data-confirmacao]", raiz);
      area.innerHTML = `<form class="formulario" novalidate><div class="bloco-topo"><h2>Confirme o pagamento</h2></div>
          ${cartaoDestinatario(leitura.destinatario)}
          ${leitura.valor ? `<div><span class="rotulo">Valor</span><p class="valor-cobrado"><strong>${moeda(leitura.valor)}</strong></p></div>`
            : `<label class="campo valor-grande"><span>Valor</span><input name="valor" inputmode="decimal" placeholder="0,00"></label>`}
          ${leitura.descricao ? `<p class="suave">Descrição: ${escapar(leitura.descricao)}</p>` : ""}
          <p class="erro-form" hidden></p>
          <div class="acoes"><button class="botao primario" type="submit">${icone("pix")} Pagar</button></div></form>`;
      const confirmarForm = $("form", area);
      confirmarForm.addEventListener("submit", async (ev) => {
        ev.preventDefault();
        const erro2 = $(".erro-form", confirmarForm);
        erro2.hidden = true;
        const valor = leitura.valor ? null : lerValor(confirmarForm.valor.value);
        if (!leitura.valor && !(valor > 0)) {
          erro2.textContent = "Digite o valor a pagar.";
          erro2.hidden = false;
          return;
        }
        try {
          const comprovante = await api("/api/pix/copia-e-cola/pagamento", {
            metodo: "POST", cabecalhos: { "Idempotency-Key": chave }, corpo: { conta: contaAtual, codigo: form.codigo.value, valor },
          });
          mostrarComprovante(comprovante);
          await pixColar(raiz, ctx);
        } catch (falha) {
          erro2.textContent = falha.message;
          erro2.hidden = false;
        }
      });
    } catch (falha) {
      erro.textContent = falha.message;
      erro.hidden = false;
    }
  });
}

async function pixChaves(raiz, ctx) {
  const chaves = await api(`/api/pix/contas/${contaAtual}/chaves`);
  raiz.innerHTML = `<div class="grade-2">
      <section class="bloco"><div class="bloco-topo"><h2>Minhas chaves</h2><span class="suave">${chaves.length} de 5</span></div>
        ${chaves.length ? chaves.map((c) => `<div class="chave"><div><span class="rotulo">${escapar(c.tipoNome)}</span>
            <div class="mono">${escapar(c.exibicao)}</div></div>
            <button type="button" class="botao perigo pequeno" data-excluir="${c.id}" aria-label="Excluir chave">${icone("lixo")}</button></div>`).join("")
          : `<div class="vazio">${icone("pix")}<span>Nenhuma chave cadastrada.</span></div>`}
      </section>
      <section class="bloco"><form class="formulario" novalidate>
        <div class="bloco-topo"><h2>Nova chave</h2></div>
        <label class="campo"><span>Tipo</span><select name="tipo">
          <option value="CPF">CPF (o seu)</option><option value="EMAIL">E-mail</option>
          <option value="TELEFONE">Celular</option><option value="ALEATORIA">Chave aleatória</option></select></label>
        <label class="campo" data-valor><span>Valor</span><input name="valor" autocomplete="off"></label>
        <p class="erro-form" hidden></p>
        <div class="acoes"><button class="botao primario" type="submit" ${chaves.length >= 5 ? "disabled" : ""}>Cadastrar</button></div>
      </form></section></div>`;
  const form = $("form", raiz);
  const atualizarCampo = () => {
    const tipo = form.tipo.value;
    $("[data-valor]", raiz).hidden = tipo === "CPF" || tipo === "ALEATORIA";
    form.valor.placeholder = tipo === "EMAIL" ? "voce@exemplo.com" : "(11) 98765-4321";
  };
  form.tipo.addEventListener("change", atualizarCampo);
  atualizarCampo();
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      await api(`/api/pix/contas/${contaAtual}/chaves`, { metodo: "POST", corpo: { tipo: form.tipo.value, valor: form.valor.value } });
      avisar("Chave cadastrada.", { titulo: "Pix" });
      await pixChaves(raiz, ctx);
    } catch (falha) {
      const erro = $(".erro-form", form);
      erro.textContent = falha.message;
      erro.hidden = false;
    }
  });
  $$("[data-excluir]", raiz).forEach((b) => b.addEventListener("click", async () => {
    if (!(await confirmar("Excluir chave", "Quem tentar pagar por essa chave não vai mais encontrar você.", { rotulo: "Excluir", perigo: true }))) return;
    try {
      await api("/api/pix/chaves/" + b.dataset.excluir, { metodo: "DELETE" });
      await pixChaves(raiz, ctx);
    } catch (falha) {
      avisarErro(falha);
    }
  }));
}

async function pixLimites(raiz) {
  const l = await api(`/api/pix/contas/${contaAtual}/limites`);
  raiz.innerHTML = `<div class="grade-3">
      <section class="kpi"><small>Período atual</small><strong>${l.noturno ? "Noturno" : "Diurno"}</strong><small>termina às ${new Date(l.fimDoPeriodo).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit", timeZone: "America/Sao_Paulo" })}</small></section>
      <section class="kpi"><small>Usado no período</small><strong class="sensivel">${moeda(l.usadoNoPeriodo)}</strong><small>de ${moeda(l.limitePeriodo)}</small></section>
      <section class="kpi"><small>Disponível agora</small><strong class="sensivel">${moeda(l.disponivelNoPeriodo)}</strong><small>por Pix</small></section>
    </div>
    <section class="bloco espaco-topo"><p class="suave">${icone("relogio")} Como no regulamento do Banco Central, das 20h às 6h vale um limite menor (R$ 1.000,00 no período), para reduzir golpes e sequestros relâmpago. De dia, o limite é de R$ 20.000,00.</p></section>`;
}

// ---------- Extrato ----------

export async function extrato(principal, ctx) {
  const contas = await minhasContas();
  const de = parametro("de") || hojeISO(-29);
  const ate = parametro("ate") || hojeISO();
  const e = await api(`/api/app/contas/${contaAtual}/extrato?de=${de}&ate=${ate}`);
  const lancamentos = [...e.lancamentos].reverse();
  principal.innerHTML = `<div class="topo"><div><h1>Extrato</h1><p>Ag. ${e.conta.agencia} · Conta ${e.conta.numero}</p></div>
      <div class="topo-acoes">${seletorDeConta(contas)}${botaoPrivacidade()}</div></div>
    <section class="bloco grade">
      <form class="linha-campos" data-periodo>
        <label class="campo"><span>De</span><input type="date" name="de" value="${e.de}"></label>
        <label class="campo"><span>Até</span><input type="date" name="ate" value="${e.ate}"></label>
        <div class="acoes"><button class="botao" type="submit">Filtrar</button>
          <a class="botao" href="/api/app/contas/${e.conta.numero}/extrato.csv?de=${e.de}&ate=${e.ate}" download>${icone("baixar")} CSV</a></div>
      </form>
      <div class="grade-4">
        <div class="kpi"><small>Saldo anterior</small><strong class="sensivel">${moeda(e.saldoInicial)}</strong></div>
        <div class="kpi"><small>Entradas</small><strong class="entrada sensivel">${moeda(e.entradas)}</strong></div>
        <div class="kpi"><small>Saídas</small><strong class="saida sensivel">${moeda(e.saidas)}</strong></div>
        <div class="kpi"><small>Saldo no fim</small><strong class="sensivel">${moeda(e.saldoFinal)}</strong></div>
      </div>
      <div class="lista">${listaDeLancamentos(lancamentos)}</div>
    </section>`;
  ligarSeletorDeConta(principal, ctx.recarregar);
  ligarComprovantes(principal);
  $("[data-periodo]", principal).addEventListener("submit", (ev) => {
    ev.preventDefault();
    const f = ev.target;
    location.hash = `#/extrato?de=${f.de.value}&ate=${f.ate.value}`;
  });
}

// ---------- Caixinhas ----------

export async function caixinhas(principal, ctx) {
  const contas = await minhasContas();
  const lista = await api(`/api/caixinhas?conta=${contaAtual}`);
  const total = lista.reduce((s, c) => s + Number(c.saldo), 0);
  principal.innerHTML = `<div class="topo"><div><h1>Caixinhas</h1><p>Separe dinheiro por objetivo. Total guardado: <span class="sensivel">${moeda(total)}</span></p></div>
      <div class="topo-acoes">${seletorDeConta(contas)}${botaoPrivacidade()}<button class="botao primario" type="button" data-nova>${icone("novo")} Nova caixinha</button></div></div>
    <div class="grade-3">${lista.length ? lista.map((c) => `<section class="bloco grade">
        <div class="bloco-topo"><h3>${escapar(c.nome)}</h3><span class="icone-botao">${icone("caixinha")}</span></div>
        <div><span class="rotulo">Guardado</span><p class="sensivel"><strong>${moeda(c.saldo)}</strong>${c.meta ? ` <span class="suave">de ${moeda(c.meta)}</span>` : ""}</p></div>
        ${c.meta ? `<div class="medidor" data-progresso="${c.progresso}"><span></span></div><small class="suave">${c.progresso}% da meta</small>` : `<small class="suave">Sem meta definida</small>`}
        <div class="acoes"><button class="botao pequeno" data-resgatar="${c.id}">Resgatar</button>
          <button class="botao primario pequeno" data-guardar="${c.id}">Guardar</button>
          ${Number(c.saldo) === 0 ? `<button class="botao perigo pequeno" data-excluir="${c.id}" aria-label="Excluir">${icone("lixo")}</button>` : ""}</div>
      </section>`).join("") : `<section class="bloco vazio">${icone("caixinha")}<span>Nenhuma caixinha ainda. Que tal começar uma reserva de emergência?</span></section>`}</div>`;
  ligarSeletorDeConta(principal, ctx.recarregar);
  $$("[data-progresso]", principal).forEach((m) => { $("span", m).style.width = m.dataset.progresso + "%"; });

  const movimentar = (id, tipo) => {
    const caixinha = lista.find((c) => String(c.id) === id);
    const corpo = abrirModal(tipo === "guardar" ? "Guardar em " + caixinha.nome : "Resgatar de " + caixinha.nome, `<form class="formulario" novalidate>
        <label class="campo valor-grande"><span>Valor</span><input name="valor" inputmode="decimal" placeholder="0,00"></label>
        <small class="dica">${tipo === "guardar" ? "Só sai do saldo em conta; o cheque especial não vai para a caixinha." : "Na caixinha: " + moeda(caixinha.saldo)}</small>
        <p class="erro-form" hidden></p><div class="acoes"><button class="botao primario" type="submit">${tipo === "guardar" ? "Guardar" : "Resgatar"}</button></div></form>`);
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
        await api(`/api/caixinhas/${id}/${tipo}`, { metodo: "POST", corpo: { valor } });
        fecharModal();
        avisar(`${moeda(valor)} ${tipo === "guardar" ? "guardados" : "resgatados"}.`, { titulo: caixinha.nome });
        ctx.recarregar();
      } catch (falha) {
        erro.textContent = falha.message;
        erro.hidden = false;
      }
    });
  };
  $$("[data-guardar]", principal).forEach((b) => b.addEventListener("click", () => movimentar(b.dataset.guardar, "guardar")));
  $$("[data-resgatar]", principal).forEach((b) => b.addEventListener("click", () => movimentar(b.dataset.resgatar, "resgatar")));
  $$("[data-excluir]", principal).forEach((b) => b.addEventListener("click", async () => {
    if (!(await confirmar("Excluir caixinha", "A caixinha está vazia e será removida.", { rotulo: "Excluir", perigo: true }))) return;
    try {
      await api("/api/caixinhas/" + b.dataset.excluir, { metodo: "DELETE" });
      ctx.recarregar();
    } catch (falha) {
      avisarErro(falha);
    }
  }));
  $("[data-nova]", principal).addEventListener("click", () => {
    const corpo = abrirModal("Nova caixinha", `<form class="formulario" novalidate>
        <label class="campo"><span>Nome</span><input name="nome" maxlength="40" placeholder="Ex.: Viagem de férias"></label>
        <label class="campo"><span>Meta (opcional)</span><input name="meta" inputmode="decimal" placeholder="0,00"></label>
        <p class="erro-form" hidden></p><div class="acoes"><button class="botao primario" type="submit">Criar</button></div></form>`);
    const form = $("form", corpo);
    form.nome.focus();
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const erro = $(".erro-form", form);
      const meta = form.meta.value.trim() ? lerValor(form.meta.value) : null;
      if (form.meta.value.trim() && !(meta > 0)) {
        erro.textContent = "Meta inválida.";
        erro.hidden = false;
        return;
      }
      try {
        await api("/api/caixinhas", { metodo: "POST", corpo: { conta: contaAtual, nome: form.nome.value, meta } });
        fecharModal();
        ctx.recarregar();
      } catch (falha) {
        erro.textContent = falha.message;
        erro.hidden = false;
      }
    });
  });
}

// ---------- Cartão virtual ----------

let temporizadorCvv = null;

export async function cartao(principal, ctx) {
  await minhasContas();
  const c = await api(`/api/cartao/${contaAtual}`);
  clearInterval(temporizadorCvv);
  principal.innerHTML = `<div class="topo"><div><h1>Cartão virtual</h1><p>Débito, para compras online. O CVV muda a cada 5 minutos.</p></div></div>
    <div class="grade-2">
      <section class="grade">
        <div class="cartao-virtual ${c.bloqueado ? "bloqueado" : ""}">
          <div class="topo-cartao"><span class="chip"></span><img src="favicon.svg" alt="" width="36" height="36"></div>
          <div class="numero" data-numero>•••• •••• •••• ${escapar(c.finalDoNumero)}</div>
          <div class="rodape-cartao"><span>Titular<strong>${escapar(c.nomeImpresso)}</strong></span>
            <span>Validade<strong>${escapar(c.validade)}</strong></span><span>CVV<strong data-cvv>•••</strong></span></div>
        </div>
        <p class="suave" data-validade-cvv></p>
      </section>
      <section class="bloco grade">
        <div><span class="rotulo">Situação</span><p>${c.bloqueado ? "Bloqueado: compras recusadas" : "Ativo"}</p></div>
        <button type="button" class="botao primario" data-revelar ${c.bloqueado ? "disabled" : ""}>${icone("olho")} Ver dados do cartão</button>
        <button type="button" class="botao ${c.bloqueado ? "" : "perigo"}" data-bloqueio>${icone("cadeado")} ${c.bloqueado ? "Desbloquear cartão" : "Bloquear temporariamente"}</button>
        <p class="suave">${icone("escudo")} O CVV não é guardado em lugar nenhum: é calculado na hora (HMAC-SHA256 do número + janela de 5 minutos). Cada visualização fica registrada na auditoria.</p>
      </section>
    </div>`;
  $("[data-revelar]", principal).addEventListener("click", async () => {
    try {
      const d = await api(`/api/cartao/${contaAtual}/dados`, { metodo: "POST" });
      $("[data-numero]", principal).textContent = d.numero.replace(/(\d{4})(?=\d)/g, "$1 ");
      $("[data-cvv]", principal).textContent = d.cvv;
      const aviso = $("[data-validade-cvv]", principal);
      const atualizar = () => {
        const s = Math.max(0, Math.round((new Date(d.cvvValidoAte) - Date.now()) / 1000));
        aviso.textContent = s > 0 ? `CVV válido por mais ${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}.` : "CVV expirou. Veja os dados de novo.";
        if (s === 0) {
          clearInterval(temporizadorCvv);
          $("[data-cvv]", principal).textContent = "•••";
        }
      };
      atualizar();
      clearInterval(temporizadorCvv);
      temporizadorCvv = setInterval(() => {
        if (!document.body.contains(aviso)) return clearInterval(temporizadorCvv);
        atualizar();
      }, 1000);
    } catch (falha) {
      avisarErro(falha);
    }
  });
  $("[data-bloqueio]", principal).addEventListener("click", async () => {
    try {
      await api(`/api/cartao/${contaAtual}/bloqueio`, { metodo: "PUT", corpo: { bloqueado: !c.bloqueado } });
      avisar(c.bloqueado ? "Cartão desbloqueado." : "Cartão bloqueado. Nenhuma compra será aprovada.", { titulo: "Cartão" });
      ctx.recarregar();
    } catch (falha) {
      avisarErro(falha);
    }
  });
}
