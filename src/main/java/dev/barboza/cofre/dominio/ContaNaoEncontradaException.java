package dev.barboza.cofre.dominio;

public class ContaNaoEncontradaException extends RuntimeException {

    public ContaNaoEncontradaException(String numero) {
        super("Conta " + numero + " não encontrada.");
    }
}
