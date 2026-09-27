package dev.barboza.cofre.seguranca;

/** Login ou senha errados (mensagem única de propósito). Vira HTTP 401. */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("Login ou senha incorretos.");
    }
}
