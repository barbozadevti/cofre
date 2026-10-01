package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.barboza.cofre.dominio.RecursoNaoEncontradoException;
import dev.barboza.cofre.pix.PixService;
import dev.barboza.cofre.pix.TipoChavePix;
import dev.barboza.cofre.servico.Comprovante;

@TesteIntegrado
class PixServiceTest {

    @Autowired
    PixService pix;

    @Autowired
    Cenario cenario;

    @Autowired
    RelogioFixo.Ajustavel relogio;

    @AfterEach
    void voltarRelogio() {
        relogio.voltar();
    }

    private static BigDecimal v(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    void cadastraChavesDeTodosOsTipos() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "0", "0");
        String n = ana.conta().getNumero();

        pix.cadastrar(ana.usuario(), n, TipoChavePix.CPF, null);
        pix.cadastrar(ana.usuario(), n, TipoChavePix.EMAIL, "Ana.Souza@Exemplo.com");
        pix.cadastrar(ana.usuario(), n, TipoChavePix.TELEFONE, "(21) 99876-5432");
        pix.cadastrar(ana.usuario(), n, TipoChavePix.ALEATORIA, null);

        assertThat(pix.chaves(ana.usuario(), n)).extracting(c -> c.getValor())
                .contains(ana.cliente().getCpf(), "ana.souza@exemplo.com", "+5521998765432")
                .anyMatch(v -> v.matches("[0-9a-f-]{36}"));
    }

    @Test
    void regrasDasChaves() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "0", "0");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "0", "0");
        String n = ana.conta().getNumero();

        assertThatThrownBy(() -> pix.cadastrar(ana.usuario(), n, TipoChavePix.CPF, joao.cliente().getCpf()))
                .hasMessage("A chave CPF precisa ser o CPF do titular da conta.");
        pix.cadastrar(ana.usuario(), n, TipoChavePix.EMAIL, "igual@teste.dev");
        assertThatThrownBy(() -> pix.cadastrar(joao.usuario(), joao.conta().getNumero(), TipoChavePix.EMAIL, "IGUAL@teste.dev"))
                .hasMessage("Essa chave já está cadastrada no Cofre.");
        for (int i = 0; i < 4; i++) {
            pix.cadastrar(ana.usuario(), n, TipoChavePix.ALEATORIA, null);
        }
        assertThatThrownBy(() -> pix.cadastrar(ana.usuario(), n, TipoChavePix.ALEATORIA, null))
                .hasMessage("Cada conta pode ter no máximo 5 chaves Pix.");
    }

    @Test
    void consultaMostraNomeECpfMascarado() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "0", "0");
        cenario.chave(ana, TipoChavePix.CPF, null);
        String cpf = ana.cliente().getCpf();
        String cpfFormatado = cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);

        PixService.Destinatario d = pix.consultar(cpfFormatado);

        assertThat(d.nome()).isEqualTo("Ana Souza");
        assertThat(d.cpfMascarado()).isEqualTo("***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**");
        assertThat(d.conta()).startsWith("***");
        assertThatThrownBy(() -> pix.consultar("ninguem@teste.dev")).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void enviaPixPorQualquerFormatoDeChave() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "1000", "0");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "0", "0");
        cenario.chave(joao, TipoChavePix.TELEFONE, "31987651234");

        Comprovante c = pix.enviar(ana.usuario(), ana.conta().getNumero(), "+55 (31) 98765-1234", v("250"), "Show", null);

        assertThat(c.tipo()).isEqualTo("PIX");
        assertThat(c.idTransacao()).startsWith("E31415926");
        assertThat(c.destino().nome()).isEqualTo("Joao Silva");
        assertThat(joao.conta().getSaldo()).isEqualTo(v("250.00"));
    }

    @Test
    void naoEnviaParaAPropriaConta() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "1000", "0");
        cenario.chave(ana, TipoChavePix.EMAIL, "ana@teste.dev");
        assertThatThrownBy(() -> pix.enviar(ana.usuario(), ana.conta().getNumero(), "ana@teste.dev", v("1"), null, null))
                .hasMessage("Essa chave é da própria conta de origem.");
    }

    @Test
    void limiteNoturnoDeMilReaisSomaOPeriodo() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "5000", "0");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "0", "0");
        cenario.chave(joao, TipoChavePix.EMAIL, "joao@teste.dev");
        String n = ana.conta().getNumero();

        relogio.ajustar(Instant.parse("2026-09-27T00:30:00Z")); // 21:30 em Brasília
        // Destinatário novo, à noite, conta aberta hoje: o antifraude pede confirmação (testado em RiscoPixTest).
        pix.enviar(ana.usuario(), n, "joao@teste.dev", v("700"), null, null, true);
        relogio.ajustar(Instant.parse("2026-09-27T07:00:00Z")); // 04:00: mesma madrugada
        assertThat(pix.limites(ana.usuario(), n).disponivelNoPeriodo()).isEqualTo(v("300.00"));
        assertThatThrownBy(() -> pix.enviar(ana.usuario(), n, "joao@teste.dev", v("300.01"), null, null))
                .hasMessage("Limite do Pix noturno (20h às 6h) excedido: disponível R$ 300,00.");

        relogio.ajustar(Instant.parse("2026-09-27T09:05:00Z")); // 06:05: começou o período diurno
        PixService.Limites dia = pix.limites(ana.usuario(), n);
        assertThat(dia.noturno()).isFalse();
        assertThat(dia.disponivelNoPeriodo()).isEqualTo(v("20000.00"));
        pix.enviar(ana.usuario(), n, "joao@teste.dev", v("2000"), null, null);
    }

    @Test
    void cobrancaComQrCodeEPagamentoPorCopiaECola() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "0", "0");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "500", "0");
        cenario.chave(ana, TipoChavePix.EMAIL, "ana@teste.dev");

        PixService.Cobranca cobranca = pix.cobrar(ana.usuario(), ana.conta().getNumero(), null, v("89.90"), "Aula avulsa");
        assertThat(cobranca.qrCodeSvg()).startsWith("<svg");
        assertThat(cobranca.copiaECola()).startsWith("000201").contains("ana@teste.dev").contains("540589.90");

        PixService.LeituraCopiaECola leitura = pix.lerCopiaECola(cobranca.copiaECola());
        assertThat(leitura.destinatario().nome()).isEqualTo("Ana Souza");
        assertThat(leitura.valor()).isEqualByComparingTo("89.90");

        // O valor do código prevalece sobre o informado.
        Comprovante c = pix.pagarCopiaECola(joao.usuario(), joao.conta().getNumero(), cobranca.copiaECola(), v("1"), null);
        assertThat(c.valor()).isEqualTo(v("89.90"));
        assertThat(c.mensagem()).isEqualTo("Aula avulsa");
        assertThat(ana.conta().getSaldo()).isEqualTo(v("89.90"));
    }

    @Test
    void semChaveNaoDaParaCobrar() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "0", "0");
        assertThatThrownBy(() -> pix.cobrar(ana.usuario(), ana.conta().getNumero(), null, null, null))
                .hasMessage("Cadastre uma chave Pix para poder receber.");
    }

    @Test
    void soODonoExcluiAChave() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "0", "0");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "0", "0");
        cenario.chave(ana, TipoChavePix.EMAIL, "ana@teste.dev");
        Long id = pix.chaves(ana.usuario(), ana.conta().getNumero()).getFirst().getId();

        assertThatThrownBy(() -> pix.excluir(joao.usuario(), id)).isInstanceOf(RecursoNaoEncontradoException.class);
        pix.excluir(ana.usuario(), id);
        assertThat(pix.chaves(ana.usuario(), ana.conta().getNumero())).isEmpty();
    }
}
