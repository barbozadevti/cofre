package dev.barboza.cofre.dominio;

public class ContaNaoEncontradaException extends RecursoNaoEncontradoException {

    public ContaNaoEncontradaException(String numero) {
        super("Conta " + numero + " não encontrada.");
    }
}
