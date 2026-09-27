package dev.barboza.cofre.seguranca;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import dev.barboza.cofre.auditoria.Auditoria;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;

/**
 * Login com bloqueio após 5 senhas erradas seguidas. A resposta para "usuário não existe" e
 * "senha errada" é a mesma (e leva o mesmo tempo), para não revelar quem é cliente.
 */
@Service
public class AutenticacaoService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder senhas;
    private final Auditoria auditoria;
    private final TransactionTemplate transacao;
    private final Clock relogio;
    private final String hashFalso;

    public AutenticacaoService(UsuarioRepository usuarios, PasswordEncoder senhas, Auditoria auditoria,
            TransactionTemplate transacao, Clock relogio) {
        this.usuarios = usuarios;
        this.senhas = senhas;
        this.auditoria = auditoria;
        this.transacao = transacao;
        this.relogio = relogio;
        this.hashFalso = senhas.encode("senha-que-ninguem-usa-2026");
    }

    public record Resultado(UsuarioLogado usuario, boolean trocarSenha) {
    }

    /** Desfecho da tentativa, decidido dentro da transação; as exceções saem só depois do commit. */
    private record Tentativa(Resultado resultado, long minutosBloqueado) {
    }

    public Resultado autenticar(String login, String senha) {
        String texto = login == null ? "" : login.trim().toLowerCase();
        String digitos = texto.replaceAll("\\D", "");
        String senhaInformada = senha == null ? "" : senha;
        Instant agora = relogio.instant();

        Tentativa tentativa = transacao.execute(status -> {
            Optional<Usuario> encontrado = usuarios.porLoginOuCpf(texto, digitos.length() == 11 ? digitos : "#");
            if (encontrado.isEmpty() || !encontrado.get().isAtivo()) {
                senhas.matches(senhaInformada, hashFalso);
                auditoria.registrarAvulso(texto.isEmpty() ? "(vazio)" : texto, null, "LOGIN_FALHOU", "Usuário inexistente");
                return new Tentativa(null, 0);
            }
            Usuario usuario = encontrado.get();
            if (usuario.bloqueadoEm(agora)) {
                return new Tentativa(null, Math.max(1, Duration.between(agora, usuario.getBloqueadoAte()).toMinutes() + 1));
            }
            if (!senhas.matches(senhaInformada, usuario.getSenhaHash())) {
                boolean bloqueou = usuario.registrarFalha(agora);
                auditoria.registrarAvulso(usuario.getLogin(), usuario.getPerfil().name(),
                        bloqueou ? "USUARIO_BLOQUEADO" : "LOGIN_FALHOU",
                        bloqueou ? Usuario.TENTATIVAS_ANTES_DO_BLOQUEIO + " senhas erradas seguidas" : "Senha errada");
                return new Tentativa(null, bloqueou ? Usuario.TEMPO_DE_BLOQUEIO.toMinutes() : 0);
            }
            usuario.registrarSucesso();
            UsuarioLogado logado = usuario.comoLogado();
            auditoria.registrar(logado, "LOGIN", null);
            return new Tentativa(new Resultado(logado, usuario.isTrocarSenha()), 0);
        });

        if (tentativa.minutosBloqueado() > 0) {
            throw new UsuarioBloqueadoException(tentativa.minutosBloqueado());
        }
        if (tentativa.resultado() == null) {
            throw new CredenciaisInvalidasException();
        }
        return tentativa.resultado();
    }

    /** Troca a senha do próprio usuário (obrigatória no primeiro acesso com senha provisória). */
    public void trocarSenha(UsuarioLogado quem, String atual, String nova) {
        transacao.executeWithoutResult(status -> {
            Usuario usuario = usuarios.findById(quem.id()).orElseThrow(CredenciaisInvalidasException::new);
            if (!senhas.matches(atual == null ? "" : atual, usuario.getSenhaHash())) {
                throw new OperacaoInvalidaException("A senha atual não confere.");
            }
            PoliticaDeSenha.validar(nova, usuario);
            if (senhas.matches(nova, usuario.getSenhaHash())) {
                throw new OperacaoInvalidaException("A nova senha precisa ser diferente da atual.");
            }
            usuario.trocarSenha(senhas.encode(nova), false);
            auditoria.registrar(quem, "SENHA_ALTERADA", null);
        });
    }
}
