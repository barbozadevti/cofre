package dev.barboza.cofre.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RelogioConfig {

    /** Relógio injetável: os testes trocam por um relógio fixo. */
    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
