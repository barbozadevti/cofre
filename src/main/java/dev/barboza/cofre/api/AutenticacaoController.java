package dev.barboza.cofre.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.barboza.cofre.seguranca.AutenticacaoService;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Login por sessão (cookie) com proteção CSRF")
public class AutenticacaoController {

    private final AutenticacaoService autenticacao;
    private final SecurityContextRepository contextos;

    public AutenticacaoController(AutenticacaoService autenticacao, SecurityContextRepository contextos) {
        this.autenticacao = autenticacao;
        this.contextos = contextos;
    }

    public record Credenciais(@NotBlank(message = "Informe o e-mail ou CPF.") String login,
            @NotBlank(message = "Informe a senha.") String senha) {
    }

    public record TrocaDeSenha(@NotBlank(message = "Informe a senha atual.") String atual,
            @NotBlank(message = "Informe a nova senha.") String nova) {
    }

    public record Sessao(String nome, String login, String perfil, boolean trocarSenha) {
    }

    @Operation(summary = "Entrar", description = "Clientes podem usar o e-mail ou o CPF. 5 senhas erradas bloqueiam por 15 minutos.")
    @PostMapping("/entrar")
    public Sessao entrar(@Valid @RequestBody Credenciais credenciais, HttpServletRequest req, HttpServletResponse res) {
        AutenticacaoService.Resultado r = autenticacao.autenticar(credenciais.login(), credenciais.senha());
        UsuarioLogado u = r.usuario();
        var token = UsernamePasswordAuthenticationToken.authenticated(u, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.perfil().name())));
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(token);
        SecurityContextHolder.setContext(contexto);
        req.getSession(true);
        req.changeSessionId();
        contextos.saveContext(contexto, req, res);
        req.getSession().setAttribute("trocarSenha", r.trocarSenha());
        return new Sessao(u.nome(), u.login(), u.perfil().name(), r.trocarSenha());
    }

    @Operation(summary = "Quem está logado")
    @GetMapping("/eu")
    public Sessao eu(@AuthenticationPrincipal UsuarioLogado u, HttpSession sessao) {
        return new Sessao(u.nome(), u.login(), u.perfil().name(), Boolean.TRUE.equals(sessao.getAttribute("trocarSenha")));
    }

    @Operation(summary = "Sair", description = "Invalida a sessão.")
    @PostMapping("/sair")
    public ResponseEntity<Void> sair(HttpServletRequest req) {
        HttpSession sessao = req.getSession(false);
        if (sessao != null) {
            sessao.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Trocar a própria senha")
    @PostMapping("/senha")
    public ResponseEntity<Void> trocarSenha(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody TrocaDeSenha troca,
            HttpSession sessao) {
        autenticacao.trocarSenha(u, troca.atual(), troca.nova());
        sessao.setAttribute("trocarSenha", false);
        return ResponseEntity.noContent().build();
    }

    /** Garante o cookie XSRF-TOKEN antes do primeiro POST (o site chama ao abrir). */
    @Operation(summary = "Token CSRF")
    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }
}
