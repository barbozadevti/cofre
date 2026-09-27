package dev.barboza.cofre.dominio;

/** Regra de negócio violada (valor inválido, conta encerrada...). Vira HTTP 422 na API. */
public class OperacaoInvalidaException extends RuntimeException {

    public OperacaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
