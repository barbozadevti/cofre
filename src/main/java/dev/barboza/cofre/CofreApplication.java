package dev.barboza.cofre;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import dev.barboza.cofre.servico.ContaService;
import dev.barboza.cofre.terminal.TerminalBancario;

@SpringBootApplication
public class CofreApplication {

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--terminal")) {
            iniciarTerminal(args);
        } else {
            SpringApplication.run(CofreApplication.class, args);
        }
    }

    /** Modo terminal: sobe só o núcleo (sem servidor web) e abre o menu no console. */
    private static void iniciarTerminal(String[] args) {
        try (ConfigurableApplicationContext contexto = new SpringApplicationBuilder(CofreApplication.class)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .properties("spring.main.banner-mode=off", "logging.level.root=WARN", "cofre.abrir-navegador=false")
                .run(args)) {
            new TerminalBancario(contexto.getBean(ContaService.class), System.in, System.out).executar();
        }
    }
}
