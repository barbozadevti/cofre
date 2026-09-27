package dev.barboza.cofre.pix;

public enum TipoChavePix {
    CPF("CPF"),
    EMAIL("E-mail"),
    TELEFONE("Celular"),
    ALEATORIA("Chave aleatória");

    private final String nome;

    TipoChavePix(String nome) {
        this.nome = nome;
    }

    public String nome() {
        return nome;
    }
}
