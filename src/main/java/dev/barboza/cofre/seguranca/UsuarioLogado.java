package dev.barboza.cofre.seguranca;

import java.io.Serializable;

/** Quem está operando. Vai na sessão HTTP e é passado explicitamente aos serviços, que decidem o acesso. */
public record UsuarioLogado(Long id, String login, String nome, Perfil perfil, Long clienteId) implements Serializable {

    /** Operador interno (juros automáticos, dados de demonstração). */
    public static final UsuarioLogado SISTEMA = new UsuarioLogado(0L, "sistema", "Sistema", Perfil.GERENTE, null);

    public boolean eh(Perfil... perfis) {
        for (Perfil p : perfis) {
            if (p == perfil) {
                return true;
            }
        }
        return false;
    }
}
