package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import dev.barboza.cofre.dominio.ContaRepository;
import dev.barboza.cofre.dominio.LancamentoRepository;
import dev.barboza.cofre.dominio.SaldoInsuficienteException;
import dev.barboza.cofre.servico.ContaService;

/** Saques simultâneos na mesma conta nunca deixam o saldo negativo nem perdem lançamentos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(RelogioFixo.class)
class ConcorrenciaTest {

    @Autowired
    ContaService servico;

    @Autowired
    ContaRepository contas;

    @Autowired
    LancamentoRepository lancamentos;

    @AfterEach
    void limpar() {
        lancamentos.deleteAll();
        contas.deleteAll();
    }

    @Test
    void saquesSimultaneosRespeitamOSaldo() throws Exception {
        String numero = servico.abrir("Ana Souza", null, new BigDecimal("500")).getNumero();
        int tentativas = 20;
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < tentativas; i++) {
                resultados.add(pool.submit(() -> {
                    largada.await();
                    try {
                        servico.sacar(numero, new BigDecimal("100"));
                        return true;
                    } catch (OptimisticLockingFailureException | SaldoInsuficienteException e) {
                        return false;
                    }
                }));
            }
            largada.countDown();
        }

        long sucessos = 0;
        for (Future<Boolean> r : resultados) {
            if (r.get()) {
                sucessos++;
            }
        }
        BigDecimal saldo = servico.buscar(numero).getSaldo();
        int lancamentosDoExtrato = servico.extrato(numero, null, null).lancamentos().size();

        assertThat(saldo.signum()).isGreaterThanOrEqualTo(0);
        assertThat(saldo).isEqualByComparingTo(new BigDecimal(500 - 100 * sucessos));
        assertThat(lancamentosDoExtrato).isEqualTo(1 + (int) sucessos);
    }
}
