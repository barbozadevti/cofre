package dev.barboza.cofre.pix;

/** O Pix tem nota de risco alta: só sai se o cliente confirmar que conhece o destinatário. Vira HTTP 428. */
public class ConfirmacaoDeRiscoException extends RuntimeException {

    private final RiscoPix.Avaliacao avaliacao;

    public ConfirmacaoDeRiscoException(RiscoPix.Avaliacao avaliacao) {
        super("Para sua segurança, confirme este Pix: encontramos sinais comuns em golpes.");
        this.avaliacao = avaliacao;
    }

    public RiscoPix.Avaliacao getAvaliacao() {
        return avaliacao;
    }
}
