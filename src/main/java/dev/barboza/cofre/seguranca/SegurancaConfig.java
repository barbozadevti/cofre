package dev.barboza.cofre.seguranca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Sessão HTTP (cookie HttpOnly + SameSite) com proteção CSRF no padrão de SPA: o token vai num
 * cookie legível pelo JavaScript e volta no cabeçalho X-XSRF-TOKEN. Perfis viram papéis ROLE_*.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SegurancaConfig {

    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    /** O Swagger UI usa estilos embutidos; a página dele recebe uma política própria. */
    private static final String CSP_SWAGGER = CSP.replace("style-src 'self'", "style-src 'self' 'unsafe-inline'");

    @Bean
    SecurityFilterChain seguranca(HttpSecurity http, SecurityContextRepository contexto) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/img/**", "/favicon.svg",
                                "/health", "/health/**", "/api/auth/entrar", "/api/auth/csrf",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/gerencia/**").hasRole(Perfil.GERENTE.name())
                        .requestMatchers("/api/diretoria/**").hasRole(Perfil.DIRETORIA.name())
                        .requestMatchers("/api/agencia/**").hasAnyRole(Perfil.CAIXA.name(), Perfil.GERENTE.name())
                        .requestMatchers("/api/app/**", "/api/pix/**", "/api/caixinhas/**", "/api/cartao/**")
                        .hasRole(Perfil.CLIENTE.name())
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.spa())
                .securityContext(s -> s.securityContextRepository(contexto))
                .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                problema(res, HttpStatus.UNAUTHORIZED, "Não autenticado", "Faça login para continuar."))
                        .accessDeniedHandler((req, res, ex) ->
                                problema(res, HttpStatus.FORBIDDEN, "Acesso negado",
                                        "Seu perfil não permite esta operação (ou a sessão expirou: recarregue a página).")))
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives(CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=()"))
                        .addHeaderWriter((req, res) -> {
                            if (req.getRequestURI().startsWith("/swagger-ui")) {
                                res.setHeader("Content-Security-Policy", CSP_SWAGGER);
                            }
                            if (req.getRequestURI().startsWith("/api/")) {
                                res.setHeader("Cache-Control", "no-store");
                            }
                        }));
        return http.build();
    }

    private static void problema(HttpServletResponse res, HttpStatus status, String titulo, String detalhe)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"title\":\"" + titulo + "\",\"status\":" + status.value() + ",\"detail\":\"" + detalhe + "\"}");
    }
}
