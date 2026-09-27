package dev.barboza.cofre.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Usado pelo atalho: quando o servidor fica pronto, abre o site no navegador padrão (Windows). */
@Component
@ConditionalOnProperty(name = "cofre.abrir-navegador", havingValue = "true")
public class AbrirNavegador {

    private static final Logger log = LoggerFactory.getLogger(AbrirNavegador.class);

    @EventListener
    public void aoFicarPronto(ApplicationReadyEvent evento) {
        if (!(evento.getApplicationContext() instanceof WebServerApplicationContext web)) {
            return;
        }
        String endereco = "http://localhost:" + web.getWebServer().getPort();
        log.info("Cofre pronto em {}", endereco);
        try {
            new ProcessBuilder("cmd", "/c", "start", "", endereco).start();
        } catch (IOException e) {
            log.warn("Não foi possível abrir o navegador; acesse {}", endereco);
        }
    }
}
