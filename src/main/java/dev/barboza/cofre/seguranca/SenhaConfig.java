package dev.barboza.cofre.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/** Beans de segurança que existem também sem servidor web (terminal e testes de serviço). */
@Configuration
public class SenhaConfig {

    /** BCrypt por padrão, com prefixo {id} para permitir trocar de algoritmo no futuro. */
    @Bean
    PasswordEncoder codificadorDeSenha() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Onde a sessão guarda quem está logado (usado pelo login da API). */
    @Bean
    SecurityContextRepository repositorioDeContexto() {
        return new HttpSessionSecurityContextRepository();
    }
}
