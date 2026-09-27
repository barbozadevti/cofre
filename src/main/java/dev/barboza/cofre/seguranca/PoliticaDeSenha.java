package dev.barboza.cofre.seguranca;

import dev.barboza.cofre.dominio.OperacaoInvalidaException;

public final class PoliticaDeSenha {

    private PoliticaDeSenha() {
    }

    /** De 8 a 72 caracteres (limite do BCrypt), com letra e número, sem conter o CPF nem o e-mail. */
    public static void validar(String senha, Usuario usuario) {
        if (senha == null || senha.length() < 8) {
            throw new OperacaoInvalidaException("A senha precisa ter pelo menos 8 caracteres.");
        }
        if (senha.length() > 72) {
            throw new OperacaoInvalidaException("A senha pode ter no máximo 72 caracteres.");
        }
        if (!senha.matches(".*\\p{L}.*") || !senha.matches(".*\\d.*")) {
            throw new OperacaoInvalidaException("A senha precisa ter letras e números.");
        }
        String minuscula = senha.toLowerCase();
        String inicioDoEmail = usuario.getLogin().split("@")[0];
        if (inicioDoEmail.length() >= 4 && minuscula.contains(inicioDoEmail)) {
            throw new OperacaoInvalidaException("A senha não pode conter o seu e-mail.");
        }
        if (usuario.getCliente() != null && senha.replaceAll("\\D", "").contains(usuario.getCliente().getCpf().substring(0, 6))) {
            throw new OperacaoInvalidaException("A senha não pode conter o seu CPF.");
        }
    }
}
