package dev.barboza.cofre.seguranca;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Com senha provisória, a única coisa liberada é trocar a senha (e sair). */
@Component
public class TravaDeSenhaProvisoria implements HandlerInterceptor, WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(this).addPathPatterns("/api/**").excludePathPatterns("/api/auth/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        HttpSession sessao = req.getSession(false);
        if (sessao != null && Boolean.TRUE.equals(sessao.getAttribute("trocarSenha"))) {
            res.setStatus(HttpStatus.FORBIDDEN.value());
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write("{\"title\":\"Troque a senha\",\"status\":403,"
                    + "\"detail\":\"Você entrou com uma senha provisória. Crie uma senha nova para continuar.\"}");
            return false;
        }
        return true;
    }
}
