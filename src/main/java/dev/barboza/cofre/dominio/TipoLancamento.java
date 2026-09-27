package dev.barboza.cofre.dominio;

public enum TipoLancamento {

    ABERTURA("Abertura de conta", true),
    DEPOSITO("Depósito", true),
    SAQUE("Saque", false),
    TRANSFERENCIA_ENVIADA("Transferência enviada", false),
    TRANSFERENCIA_RECEBIDA("Transferência recebida", true);

    private final String descricao;
    private final boolean credito;

    TipoLancamento(String descricao, boolean credito) {
        this.descricao = descricao;
        this.credito = credito;
    }

    public String descricao() {
        return descricao;
    }

    public boolean credito() {
        return credito;
    }
}
