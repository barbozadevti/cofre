// Chamadas à API: cookie de sessão + token CSRF (cookie XSRF-TOKEN -> cabeçalho X-XSRF-TOKEN).

function lerCookie(nome) {
  const par = document.cookie.split("; ").find((c) => c.startsWith(nome + "="));
  return par ? decodeURIComponent(par.slice(nome.length + 1)) : null;
}

let aoPerderSessao = () => {};

/** Chamado quando a API responde 401 (sessão expirou): o app volta para o login. */
export function quandoPerderSessao(funcao) {
  aoPerderSessao = funcao;
}

export class ErroDaApi extends Error {
  constructor(status, titulo, detalhe, dados) {
    super(detalhe || "Não foi possível concluir a operação.");
    this.status = status;
    this.titulo = titulo || "Erro";
    this.dados = dados;
  }
}

export async function api(caminho, { metodo = "GET", corpo, cabecalhos = {} } = {}) {
  const opcoes = { method: metodo, credentials: "same-origin", headers: { Accept: "application/json", ...cabecalhos } };
  if (corpo !== undefined) {
    opcoes.headers["Content-Type"] = "application/json";
    opcoes.body = JSON.stringify(corpo);
  }
  if (metodo !== "GET") {
    const token = lerCookie("XSRF-TOKEN");
    if (token) opcoes.headers["X-XSRF-TOKEN"] = token;
  }
  let resposta;
  try {
    resposta = await fetch(caminho, opcoes);
  } catch {
    throw new ErroDaApi(0, "Sem conexão", "Não foi possível falar com o servidor. Confira se o Cofre está rodando.");
  }
  if (resposta.status === 204) return null;
  const tipo = resposta.headers.get("content-type") || "";
  const dados = tipo.includes("json") ? await resposta.json().catch(() => null) : await resposta.text();
  if (!resposta.ok) {
    const erro = new ErroDaApi(resposta.status, dados?.title, dados?.detail, typeof dados === "object" ? dados : null);
    if (resposta.status === 401 && !caminho.startsWith("/api/auth/")) aoPerderSessao(erro);
    throw erro;
  }
  return dados;
}

/** Garante o cookie do token CSRF antes do primeiro POST. */
export function prepararCsrf() {
  return api("/api/auth/csrf").catch(() => null);
}

/** Uma chave nova por operação: se o usuário clicar duas vezes, o servidor não duplica o Pix. */
export function novaChaveDeIdempotencia() {
  return crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random().toString(16).slice(2);
}
