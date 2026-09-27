package dev.barboza.cofre.seguranca;

/** Acesso bloqueado temporariamente por excesso de senhas erradas. Vira HTTP 423. */
public class UsuarioBloqueadoException extends RuntimeException {

    public UsuarioBloqueadoException(long minutos) {
        super("Acesso bloqueado por excesso de tentativas. Tente de novo em " + minutos
                + (minutos == 1 ? " minuto." : " minutos."));
    }
}
