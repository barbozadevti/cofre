package dev.barboza.cofre.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/** Documentação interativa em /swagger-ui.html (entre no site antes para usar a sessão). */
@Configuration
public class DocumentacaoConfig {

    @Bean
    OpenAPI documentacao() {
        return new OpenAPI().info(new Info()
                .title("Cofre — API do banco digital")
                .version("2.0")
                .description("""
                        Contas, cheque especial, Pix (chaves, envio, BR Code), caixinhas, cartão virtual, \
                        balcão da agência e backoffice do gerente. A autenticação é por sessão: entre pelo site \
                        (ou POST /api/auth/entrar) e o cookie é usado aqui. Operações que alteram dados exigem o \
                        cabeçalho X-XSRF-TOKEN com o valor do cookie XSRF-TOKEN.""")
                .license(new License().name("MIT")));
    }
}
