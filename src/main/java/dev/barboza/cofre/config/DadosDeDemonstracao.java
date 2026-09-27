package dev.barboza.cofre.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.ContaRepository;
import dev.barboza.cofre.servico.ContaService;

/**
 * Na primeira execução (banco vazio), cria contas com um mês de movimentação para demonstrar o sistema.
 * Roda antes de o servidor web aceitar requisições, para o site nunca abrir com a lista vazia.
 */
@Component
@ConditionalOnProperty(name = "cofre.demo", havingValue = "true")
public class DadosDeDemonstracao implements SmartInitializingSingleton {

    private final ContaService servico;
    private final ContaRepository contas;
    private final Clock relogio;

    public DadosDeDemonstracao(ContaService servico, ContaRepository contas, Clock relogio) {
        this.servico = servico;
        this.contas = contas;
        this.relogio = relogio;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (contas.count() > 0) {
            return;
        }
        LocalDate hoje = LocalDate.now(relogio.withZone(ContaService.FUSO));

        Conta mario = servico.abrirEm("Mario Andrade", "0678", valor("237.48"), em(hoje, 28, "09:12"));
        Conta ana = servico.abrirEm("Ana Souza", "0001", valor("3500.00"), em(hoje, 26, "10:40"));
        Conta joao = servico.abrirEm("João da Silva", "0001", valor("0"), em(hoje, 20, "14:05"));
        Conta helena = servico.abrirEm("Helena Prado", "0412", valor("12800.00"), em(hoje, 18, "11:30"));

        servico.depositarEm(mario.getNumero(), valor("1850.00"), em(hoje, 25, "08:03"));
        servico.transferirEm(ana.getNumero(), joao.getNumero(), valor("450.00"), em(hoje, 19, "16:22"));
        servico.sacarEm(mario.getNumero(), valor("300.00"), em(hoje, 15, "18:47"));
        servico.depositarEm(joao.getNumero(), valor("1200.00"), em(hoje, 12, "09:00"));
        servico.transferirEm(helena.getNumero(), mario.getNumero(), valor("980.50"), em(hoje, 9, "13:15"));
        servico.transferirEm(mario.getNumero(), ana.getNumero(), valor("120.00"), em(hoje, 6, "20:10"));
        servico.sacarEm(joao.getNumero(), valor("200.00"), em(hoje, 4, "12:34"));
        servico.depositarEm(helena.getNumero(), valor("2300.00"), em(hoje, 2, "10:00"));
        servico.sacarEm(ana.getNumero(), valor("80.00"), em(hoje, 1, "17:55"));
    }

    private static BigDecimal valor(String texto) {
        return new BigDecimal(texto);
    }

    private static Instant em(LocalDate hoje, int diasAtras, String hora) {
        return hoje.minusDays(diasAtras).atTime(LocalTime.parse(hora)).atZone(ContaService.FUSO).toInstant();
    }
}
