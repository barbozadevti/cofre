package dev.barboza.cofre;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Relógio dos testes: começa em 26/09/2026 12:00 (Brasília) e pode ser ajustado (ex.: Pix noturno). */
@TestConfiguration
public class RelogioFixo {

    public static final Instant AGORA = Instant.parse("2026-09-26T15:00:00Z");

    @Bean
    @Primary
    Ajustavel relogioDosTestes() {
        return new Ajustavel();
    }

    public static class Ajustavel extends Clock {

        private Instant agora = AGORA;

        public void ajustar(Instant instante) {
            this.agora = instante;
        }

        public void voltar() {
            this.agora = AGORA;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return Clock.fixed(agora, zona);
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
