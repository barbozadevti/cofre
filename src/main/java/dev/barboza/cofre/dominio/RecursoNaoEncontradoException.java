package dev.barboza.cofre.dominio;

/** Algo pedido não existe (ou o usuário não pode saber que existe). Vira HTTP 404. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
