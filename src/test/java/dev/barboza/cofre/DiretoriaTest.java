package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import dev.barboza.cofre.config.DadosDeDemonstracao;
import dev.barboza.cofre.diretoria.DiretoriaService;
import dev.barboza.cofre.dominio.AcessoNegadoException;
import dev.barboza.cofre.seguranca.AutenticacaoService;
import dev.barboza.cofre.seguranca.Perfil;
import dev.barboza.cofre.seguranca.UsuarioLogado;

/** Painel executivo sobre os dados de demonstração: os números fecham e só a diretoria vê. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"cofre.demo=true", "spring.datasource.url=jdbc:h2:mem:cofre-diretoria;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@Import(RelogioFixo.class)
class DiretoriaTest {

    @Autowired
    AutenticacaoService autenticacao;

    @Autowired
    DiretoriaService diretoria;

    private UsuarioLogado entrar(String login) {
        return autenticacao.autenticar(login, DadosDeDemonstracao.SENHA).usuario();
    }

    @Test
    void painelExecutivoComTodosOsBlocos() {
        UsuarioLogado ceo = entrar("diretoria@cofre.dev");
        assertThat(ceo.perfil()).isEqualTo(Perfil.DIRETORIA);
        DiretoriaService.Painel p = diretoria.painel(ceo);

        assertThat(p.base().clientes()).isEqualTo(6);
        assertThat(p.base().poupancas()).isEqualTo(4);
        assertThat(p.base().contasCorrentes()).isEqualTo(6);
        assertThat(p.custodia().total()).isEqualByComparingTo(
                p.custodia().contaCorrente().add(p.custodia().poupanca()).add(p.custodia().caixinhas()));
        assertThat(p.resultado().margem()).isEqualByComparingTo(p.resultado().receitaDeJuros().subtract(p.resultado().custoDoRendimento()));
        assertThat(p.credito().clientesNoNegativo()).isPositive();
        assertThat(p.antifraude().retidos()).isEqualTo(2);
        assertThat(p.antifraude().confirmados()).isEqualTo(1);
        assertThat(p.antifraude().desistencias()).isEqualTo(1);
        assertThat(p.escudo().contasProtegidas()).isEqualTo(1);
        assertThat(p.salario().portabilidadesConcluidas()).isEqualTo(1);
        assertThat(p.salario().emAndamento()).isEqualTo(1);
        assertThat(p.concentracao().maiores()).hasSize(5);
        assertThat(p.meses()).hasSize(6);
        assertThat(p.leituras()).isNotEmpty();
    }

    @Test
    void soADiretoriaVeEADiretoriaNaoOperaBalcao() {
        assertThatThrownBy(() -> diretoria.painel(entrar("gerente@cofre.dev"))).isInstanceOf(AcessoNegadoException.class);
        assertThat(Perfil.DIRETORIA.funcionario()).isFalse();
    }
}
