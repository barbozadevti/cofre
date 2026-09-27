package dev.barboza.cofre.servico;

import dev.barboza.cofre.dominio.AcessoNegadoException;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.ContaNaoEncontradaException;
import dev.barboza.cofre.seguranca.Perfil;
import dev.barboza.cofre.seguranca.UsuarioLogado;

/**
 * Regras de acesso por perfil. Ficam nos serviços (e não só nas rotas) para valerem igual
 * no site, na API e no terminal.
 */
public final class Acesso {

    private Acesso() {
    }

    public static void exigirFuncionario(UsuarioLogado quem) {
        if (!quem.perfil().funcionario()) {
            throw new AcessoNegadoException("Operação disponível só para funcionários da agência.");
        }
    }

    public static void exigirGerente(UsuarioLogado quem) {
        if (quem.perfil() != Perfil.GERENTE) {
            throw new AcessoNegadoException("Operação disponível só para o gerente.");
        }
    }

    public static void exigirCliente(UsuarioLogado quem) {
        if (quem.perfil() != Perfil.CLIENTE) {
            throw new AcessoNegadoException("Operação feita pelo próprio cliente, no app.");
        }
    }

    /**
     * Cliente só enxerga as próprias contas. Para conta de outra pessoa a resposta é "não encontrada"
     * (e não "acesso negado"), para não revelar quais números de conta existem.
     */
    public static void exigirVisao(UsuarioLogado quem, Conta conta) {
        if (quem.perfil() == Perfil.CLIENTE && !conta.pertenceA(quem.clienteId())) {
            throw new ContaNaoEncontradaException(conta.getNumero());
        }
    }

    public static void exigirDono(UsuarioLogado quem, Conta conta) {
        exigirCliente(quem);
        exigirVisao(quem, conta);
    }
}
