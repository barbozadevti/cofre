package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import dev.barboza.cofre.config.DadosDeDemonstracao;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.seguranca.AutenticacaoService;
import dev.barboza.cofre.seguranca.Perfil;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.servico.ContaService;
import dev.barboza.cofre.servico.PainelService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"cofre.demo=true", "spring.datasource.url=jdbc:h2:mem:cofre-demo;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@Import(RelogioFixo.class)
class DemonstracaoTest {

    @Autowired
    AutenticacaoService autenticacao;

    @Autowired
    ContaService contas;

    @Autowired
    PainelService painel;

    @Test
    void todosOsPerfisDeDemonstracaoEntram() {
        assertThat(autenticacao.autenticar("gerente@cofre.dev", DadosDeDemonstracao.SENHA).usuario().perfil()).isEqualTo(Perfil.GERENTE);
        assertThat(autenticacao.autenticar("caixa@cofre.dev", DadosDeDemonstracao.SENHA).usuario().perfil()).isEqualTo(Perfil.CAIXA);
        assertThat(autenticacao.autenticar("mario@cofre.dev", DadosDeDemonstracao.SENHA).usuario().perfil()).isEqualTo(Perfil.CLIENTE);
    }

    @Test
    void criaSeisMesesDeHistoricoEUmClienteNoChequeEspecial() {
        UsuarioLogado gerente = autenticacao.autenticar("gerente@cofre.dev", DadosDeDemonstracao.SENHA).usuario();
        assertThat(contas.contasVisiveis(gerente)).hasSize(5);

        Conta joao = contas.pesquisar(gerente, "João").getFirst();
        assertThat(joao.getSaldo().signum()).isNegative();
        assertThat(joao.usoDoLimite()).isLessThanOrEqualTo(joao.getLimite().add(new java.math.BigDecimal("50")));
        assertThat(contas.extrato(gerente, joao.getNumero(), null, null).lancamentos())
                .extracting(l -> l.getTipo()).contains(TipoLancamento.JUROS_CHEQUE_ESPECIAL);

        UsuarioLogado mario = autenticacao.autenticar("mario@cofre.dev", DadosDeDemonstracao.SENHA).usuario();
        PainelService.Painel p = painel.painel(mario);
        assertThat(p.meses()).hasSize(6).allSatisfy(m -> assertThat(m.entradas().signum()).isPositive());
        assertThat(p.emCaixinhas().signum()).isPositive();
    }
}
