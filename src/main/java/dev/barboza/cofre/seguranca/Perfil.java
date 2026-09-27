package dev.barboza.cofre.seguranca;

public enum Perfil {

    /** Movimenta as próprias contas: Pix, transferências, caixinhas, cartão. */
    CLIENTE("Cliente"),
    /** Atende no balcão: depósito e saque em espécie, consulta de contas. */
    CAIXA("Caixa"),
    /** Tudo do caixa + abre contas, define limites, bloqueia, vê indicadores e auditoria. */
    GERENTE("Gerente");

    private final String nome;

    Perfil(String nome) {
        this.nome = nome;
    }

    public String nome() {
        return nome;
    }

    public boolean funcionario() {
        return this != CLIENTE;
    }
}
