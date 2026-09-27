package dev.barboza.cofre.dominio;

public enum SituacaoConta {
    ATIVA,
    /** Bloqueada pelo gerente: recebe dinheiro, mas não movimenta saídas. */
    BLOQUEADA,
    ENCERRADA
}
