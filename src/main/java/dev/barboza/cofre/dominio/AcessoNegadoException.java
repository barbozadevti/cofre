package dev.barboza.cofre.dominio;

/** O perfil do usuário não permite a operação. Vira HTTP 403. */
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
