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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import dev.barboza.cofre.dominio.SaldoInsuficienteException;
import dev.barboza.cofre.seguranca.Perfil;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.servico.ContaService;

/** Saques simultâneos na mesma conta nunca deixam o saldo passar do limite nem perdem lançamentos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import({RelogioFixo.class, Cenario.class})
class ConcorrenciaTest {

    @Autowired
    ContaService contas;

    @Autowired
    Cenario cenario;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void limpar() {
        for (String tabela : List.of("evento_auditoria", "lancamento", "chave_pix", "caixinha", "cartao", "conta", "usuario", "cliente")) {
            jdbc.update("delete from " + tabela);
        }
    }

    @Test
    void saquesSimultaneosRespeitamSaldoELimite() throws Exception {
        Cenario.Pessoa ana = transacao.execute(s -> cenario.cliente("Ana Souza", "500", "300"));
        UsuarioLogado caixa = transacao.execute(s -> cenario.funcionario(Perfil.CAIXA));
        String numero = ana.conta().getNumero();
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 20; i++) {
                resultados.add(pool.submit(() -> {
                    largada.await();
                    try {
                        contas.sacarEmEspecie(caixa, numero, new BigDecimal("100"));
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
        BigDecimal saldo = contas.buscar(caixa, numero).getSaldo();
        int lancamentos = contas.extrato(caixa, numero, null, null).lancamentos().size();

        assertThat(saldo).isGreaterThanOrEqualTo(new BigDecimal("-300.00"));
        assertThat(saldo).isEqualByComparingTo(new BigDecimal(500 - 100 * sucessos));
        assertThat(lancamentos).isEqualTo(1 + (int) sucessos);
    }
}
