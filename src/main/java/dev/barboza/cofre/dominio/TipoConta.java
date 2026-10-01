package dev.barboza.cofre.dominio;

public enum TipoConta {

    CORRENTE("Conta corrente"),
    POUPANCA("Conta poupança");

    private final String nome;

    TipoConta(String nome) {
        this.nome = nome;
    }

    public String nome() {
        return nome;
    }
}
