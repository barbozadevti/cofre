package dev.barboza.cofre.seguranca;

public enum Perfil {

    /** Movimenta as próprias contas: Pix, transferências, caixinhas, cartão. */
    CLIENTE("Cliente"),
    /** Atende no balcão: depósito e saque em espécie, consulta de contas. */
    CAIXA("Caixa"),
    /** Tudo do caixa + abre contas, define limites, bloqueia, vê indicadores e auditoria. */
    GERENTE("Gerente"),
    /** Diretoria (CEO): só leitura, com a visão executiva do banco inteiro. Não opera contas. */
    DIRETORIA("Diretoria");

    private final String nome;

    Perfil(String nome) {
        this.nome = nome;
    }

    public String nome() {
        return nome;
    }

    /** Funcionário de agência (opera o balcão). A diretoria não opera contas. */
    public boolean funcionario() {
        return this == CAIXA || this == GERENTE;
    }
}
