package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.servico.ContaService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"cofre.demo=true", "spring.datasource.url=jdbc:h2:mem:cofre-demo;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@Import(RelogioFixo.class)
class DemonstracaoTest {

    @Autowired
    ContaService servico;

    @Test
    void criaContasComUmMesDeMovimento() {
        assertThat(servico.listar())
                .extracting(Conta::getTitular, Conta::getSaldo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Mario Andrade", new BigDecimal("2647.98")),
                        org.assertj.core.groups.Tuple.tuple("Ana Souza", new BigDecimal("3090.00")),
                        org.assertj.core.groups.Tuple.tuple("João da Silva", new BigDecimal("1450.00")),
                        org.assertj.core.groups.Tuple.tuple("Helena Prado", new BigDecimal("14119.50")));
        assertThat(servico.extrato("10001-3", null, null).lancamentos()).hasSize(5);
    }
}
