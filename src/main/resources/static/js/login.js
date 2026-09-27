// Login (com atalhos de demonstração), troca obrigatória de senha e tela de perfil.
import { api, prepararCsrf } from "./api.js";
import { $, avisar, avisarErro, escapar, icone } from "./ui.js";

const DEMO = [
  { login: "mario@cofre.dev", titulo: "Cliente", detalhe: "Mario · Pix, caixinhas, cartão" },
  { login: "joao@cofre.dev", titulo: "Cliente no limite", detalhe: "João · cheque especial e juros" },
  { login: "caixa@cofre.dev", titulo: "Caixa", detalhe: "Antônio · depósito e saque" },
  { login: "gerente@cofre.dev", titulo: "Gerente", detalhe: "Carla · contas, limites, auditoria" },
];
const SENHA_DEMO = "Cofre@2026";

export function telaDeLogin(aoEntrar) {
  $("#app").innerHTML = `<div class="entrada-tela">
      <section class="vitrine">
        <div class="marca"><img src="favicon.svg" alt=""><div><strong>Cofre</strong><small>banco digital</small></div></div>
        <div>
          <h1>Seu dinheiro <em>guardado</em> com a seriedade de um cofre.</h1>
          <p>Pix com QR Code, cheque especial, caixinhas e cartão virtual com CVV dinâmico. E, do outro lado do balcão, o backoffice da agência.</p>
          <ul>
            <li>${icone("pix")} Pix por chave, QR Code e copia e cola (padrão BR Code do Banco Central)</li>
            <li>${icone("escudo")} Perfis de cliente, caixa e gerente, com auditoria de cada ação</li>
            <li>${icone("cadeado")} Sessão protegida, CSRF e bloqueio após 5 senhas erradas</li>
          </ul>
          <div class="cartao-virtual" aria-hidden="true">
            <div class="topo-cartao"><span class="chip"></span><img src="favicon.svg" alt="" width="34" height="34"></div>
            <div class="numero">•••• •••• •••• 2026</div>
            <div class="rodape-cartao"><span>Titular<strong>SEU NOME</strong></span><span>débito virtual</span></div>
          </div>
        </div>
        <small class="suave">Projeto de portfólio. Nenhum dinheiro de verdade circula aqui.</small>
      </section>
      <section class="painel-login">
        <div class="caixa-login">
          <div><h2>Entrar</h2><p class="suave">Use seu e-mail ou CPF.</p></div>
          <form class="formulario" id="form-login" novalidate>
            <label class="campo"><span>E-mail ou CPF</span><input id="login" autocomplete="username" required></label>
            <label class="campo"><span>Senha</span><input id="senha" type="password" autocomplete="current-password" required></label>
            <p class="erro-form" id="erro-login" role="alert" hidden></p>
            <button class="botao primario largo" type="submit">Entrar</button>
          </form>
          <div class="separador">ou experimente com uma conta de demonstração</div>
          <div class="demo"><div class="demo-botoes">
            ${DEMO.map((d) => `<button type="button" data-demo="${d.login}"><strong>${d.titulo}</strong><small>${d.detalhe}</small></button>`).join("")}
          </div><small class="suave">Senha de todas: ${SENHA_DEMO}</small></div>
        </div>
      </section>
    </div>`;

  const form = $("#form-login");
  const erro = $("#erro-login");
  const entrar = async (login, senha) => {
    erro.hidden = true;
    const botao = form.querySelector("button[type=submit]");
    botao.disabled = true;
    try {
      const sessao = await api("/api/auth/entrar", { metodo: "POST", corpo: { login, senha } });
      await prepararCsrf();
      await aoEntrar(sessao);
    } catch (e) {
      erro.textContent = e.message;
      erro.hidden = false;
    } finally {
      botao.disabled = false;
    }
  };
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    entrar($("#login").value, $("#senha").value);
  });
  document.querySelectorAll("[data-demo]").forEach((b) => b.addEventListener("click", () => {
    $("#login").value = b.dataset.demo;
    $("#senha").value = SENHA_DEMO;
    entrar(b.dataset.demo, SENHA_DEMO);
  }));
  $("#login").focus();
}

const REGRAS = "De 8 a 72 caracteres, com letras e números, sem o seu CPF ou e-mail.";

function formularioDeSenha(comAtual = true) {
  return `<form class="formulario" id="form-senha" novalidate>
      ${comAtual ? `<label class="campo"><span>Senha atual</span><input id="atual" type="password" autocomplete="current-password"></label>` : ""}
      <label class="campo"><span>Nova senha</span><input id="nova" type="password" autocomplete="new-password"><small class="dica">${REGRAS}</small></label>
      <label class="campo"><span>Repita a nova senha</span><input id="repeticao" type="password" autocomplete="new-password"></label>
      <p class="erro-form" id="erro-senha" role="alert" hidden></p>
      <button class="botao primario" type="submit">Salvar nova senha</button>
    </form>`;
}

function ligarFormularioDeSenha(aoConcluir) {
  const erro = $("#erro-senha");
  $("#form-senha").addEventListener("submit", async (e) => {
    e.preventDefault();
    erro.hidden = true;
    if ($("#nova").value !== $("#repeticao").value) {
      erro.textContent = "As duas senhas novas não são iguais.";
      erro.hidden = false;
      return;
    }
    try {
      await api("/api/auth/senha", { metodo: "POST", corpo: { atual: $("#atual").value, nova: $("#nova").value } });
      await aoConcluir();
    } catch (falha) {
      erro.textContent = falha.message;
      erro.hidden = false;
    }
  });
}

/** Primeiro acesso com senha provisória: nada funciona até criar uma senha própria. */
export function telaDeTrocaDeSenha(sessao, aoConcluir, sair) {
  $("#app").innerHTML = `<div class="painel-login"><div class="caixa-login">
      <div class="marca"><img src="favicon.svg" alt=""><div><strong>Cofre</strong><small>primeiro acesso</small></div></div>
      <div><h2>Olá, ${escapar(sessao.nome.split(" ")[0])}!</h2><p class="suave">Você entrou com a senha provisória que recebeu na agência. Crie uma senha só sua para continuar.</p></div>
      ${formularioDeSenha(true)}
      <button type="button" class="botao fantasma" data-sair>${icone("sair")} Sair</button>
    </div></div>`;
  $("label.campo span").textContent = "Senha provisória";
  ligarFormularioDeSenha(aoConcluir);
  $("[data-sair]").addEventListener("click", sair);
}

export async function telaDePerfil(principal, { sessao, sair }) {
  const perfis = { CLIENTE: "Cliente", CAIXA: "Caixa", GERENTE: "Gerente" };
  principal.innerHTML = `<div class="topo"><div><h1>Perfil</h1><p>Seus dados de acesso e segurança.</p></div></div>
    <div class="grade-2">
      <section class="bloco"><div class="bloco-topo"><h2>Trocar senha</h2></div>${formularioDeSenha(true)}</section>
      <section class="bloco grade">
        <div><span class="rotulo">Nome</span><p>${escapar(sessao.nome)}</p></div>
        <div><span class="rotulo">Login</span><p>${escapar(sessao.login)}</p></div>
        <div><span class="rotulo">Perfil</span><p>${perfis[sessao.perfil]}</p></div>
        <div class="suave">${icone("escudo")} Sessão encerrada após 20 minutos sem uso. Cinco senhas erradas seguidas bloqueiam o acesso por 15 minutos.</div>
        <button type="button" class="botao perigo" data-sair-perfil>${icone("sair")} Sair do Cofre</button>
      </section>
    </div>`;
  ligarFormularioDeSenha(async () => {
    avisar("Sua senha foi alterada.", { titulo: "Senha nova" });
    $("#form-senha").reset();
  });
  $("[data-sair-perfil]").addEventListener("click", sair);
}

export { avisarErro };
