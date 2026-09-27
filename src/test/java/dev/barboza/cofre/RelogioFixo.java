package dev.barboza.cofre;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Relógio fixo em 26/09/2026 12:00 (horário de Brasília) para testes determinísticos. */
@TestConfiguration
public class RelogioFixo {

    public static final Instant AGORA = Instant.parse("2026-09-26T15:00:00Z");

    @Bean
    @Primary
    Clock relogioFixo() {
        return Clock.fixed(AGORA, ZoneOffset.UTC);
    }
}
