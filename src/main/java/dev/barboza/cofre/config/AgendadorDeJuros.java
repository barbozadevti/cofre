package dev.barboza.cofre.config;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.barboza.cofre.servico.ContaService;
import dev.barboza.cofre.servico.JurosChequeEspecial;
import dev.barboza.cofre.servico.RendimentoPoupanca;

/**
 * Todo dia às 00:05 (Brasília): cobra os juros do cheque especial do dia anterior e credita o rendimento das
 * poupanças que fazem aniversário hoje. Desligado nos testes e no terminal.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "cofre.juros.agendado", havingValue = "true")
public class AgendadorDeJuros {

    private static final Logger log = LoggerFactory.getLogger(AgendadorDeJuros.class);

    private final JurosChequeEspecial juros;
    private final RendimentoPoupanca rendimento;
    private final Clock relogio;

    public AgendadorDeJuros(JurosChequeEspecial juros, RendimentoPoupanca rendimento, Clock relogio) {
        this.juros = juros;
        this.rendimento = rendimento;
        this.relogio = relogio;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Sao_Paulo")
    public void cobrarOntem() {
        LocalDate ontem = LocalDate.now(relogio.withZone(ContaService.FUSO)).minusDays(1);
        log.info("Juros do cheque especial de {}: {} conta(s) cobrada(s)", ontem, juros.cobrar(ontem));
        LocalDate hoje = ontem.plusDays(1);
        log.info("Rendimento da poupança de {}: {} conta(s) creditada(s)", hoje, rendimento.creditar(hoje));
    }
}
