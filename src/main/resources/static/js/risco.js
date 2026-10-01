// Antifraude no app: quando a API responde 428, mostra a nota de risco e os motivos antes de enviar o Pix.
import { $, abrirModal, escapar, fecharModal, icone } from "./ui.js";

/** Medidor em meia-lua da nota de risco (0 a 100). */
function medidor(pontuacao) {
  const angulo = Math.PI * (1 - pontuacao / 100);
  const x = 60 + 46 * Math.cos(angulo);
  const y = 60 - 46 * Math.sin(angulo);
  return `<svg viewBox="0 0 120 70" class="medidor-risco" role="img" aria-label="Nota de risco ${pontuacao} de 100">
    <path d="M14 60a46 46 0 0 1 92 0" class="trilho"/>
    <path d="M14 60A46 46 0 0 1 ${x.toFixed(1)} ${y.toFixed(1)}" class="valor"/>
    <text x="60" y="56" text-anchor="middle" class="nota">${pontuacao}</text>
  </svg>`;
}

function perguntar(problema) {
  return new Promise((resolver) => {
    const corpo = abrirModal("Confirme este Pix", `<div class="alerta-risco">
        ${medidor(problema.pontuacao)}
        <p>Encontramos sinais que aparecem em golpes. O Pix <strong>ainda não foi enviado</strong>.</p>
        <ul class="fatores">${problema.fatores.map((f) => `<li><span class="pontos">+${f.pontos}</span>${escapar(f.descricao)}</li>`).join("")}</ul>
        <p class="dica">${icone("alerta")} Ninguém do banco pede Pix por telefone. Desconfie de urgência, de parente com número novo e de "central de segurança".</p>
        <div class="acoes">
          <button type="button" class="botao" data-cancelar>Não enviar</button>
          <button type="button" class="botao perigo" data-confirmar>Conheço o destinatário, enviar</button>
        </div>
      </div>`);
    let respondeu = false;
    const responder = (sim) => {
      respondeu = true;
      fecharModal();
      resolver(sim);
    };
    $("[data-cancelar]", corpo).addEventListener("click", () => responder(false));
    $("[data-confirmar]", corpo).addEventListener("click", () => responder(true));
    corpo.closest("dialog")?.addEventListener("close", () => { if (!respondeu) resolver(false); }, { once: true });
  });
}

/**
 * Executa o envio; se a API pedir confirmação (428), mostra os motivos e, se o cliente confirmar,
 * repete com o cabeçalho X-Confirmacao-Risco. Devolve o resultado, ou null se o cliente desistiu.
 */
export async function comProtecaoContraGolpes(enviar) {
  try {
    return await enviar({});
  } catch (erro) {
    if (erro.status !== 428 || !erro.dados) throw erro;
    const confirmou = await perguntar(erro.dados);
    if (!confirmou) return null;
    return enviar({ "X-Confirmacao-Risco": "confirmo" });
  }
}
