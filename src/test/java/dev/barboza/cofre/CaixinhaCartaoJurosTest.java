package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.barboza.cofre.caixinha.Caixinha;
import dev.barboza.cofre.caixinha.CaixinhaService;
import dev.barboza.cofre.cartao.Cartao;
import dev.barboza.cofre.cartao.CartaoService;
import dev.barboza.cofre.cartao.Luhn;
import dev.barboza.cofre.dominio.LancamentoRepository;
import dev.barboza.cofre.dominio.RecursoNaoEncontradoException;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.servico.ContaService;
import dev.barboza.cofre.servico.JurosChequeEspecial;

@TesteIntegrado
class CaixinhaCartaoJurosTest {

    @Autowired
    CaixinhaService caixinhas;

    @Autowired
    CartaoService cartoes;

    @Autowired
    JurosChequeEspecial juros;

    @Autowired
    ContaService contas;

    @Autowired
    LancamentoRepository lancamentos;

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
    void guardaEResgataComMeta() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "1000", "500");
        Caixinha viagem = caixinhas.criar(ana.usuario(), ana.conta().getNumero(), "Viagem", v("2000"));

        caixinhas.guardar(ana.usuario(), viagem.getId(), v("600"));
        assertThat(viagem.getSaldo()).isEqualTo(v("600.00"));
        assertThat(viagem.progresso()).isEqualTo(30);
        assertThat(ana.conta().getSaldo()).isEqualTo(v("400.00"));

        // Só dinheiro próprio vai para a caixinha, nunca o cheque especial.
        assertThatThrownBy(() -> caixinhas.guardar(ana.usuario(), viagem.getId(), v("400.01")))
                .hasMessage("Saldo insuficiente: disponível R$ 400,00, solicitado R$ 400,01.");

        caixinhas.resgatar(ana.usuario(), viagem.getId(), v("100"));
        assertThat(viagem.getSaldo()).isEqualTo(v("500.00"));
        assertThat(ana.conta().getSaldo()).isEqualTo(v("500.00"));
        assertThatThrownBy(() -> caixinhas.resgatar(ana.usuario(), viagem.getId(), v("500.01")))
                .hasMessage("A caixinha Viagem tem só R$ 500,00.");
        assertThatThrownBy(() -> caixinhas.excluir(ana.usuario(), viagem.getId()))
                .hasMessage("Resgate os R$ 500,00 antes de excluir a caixinha.");
    }

    @Test
    void caixinhaDeOutroClienteNaoExiste() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "100", "0");
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "100", "0");
        Caixinha reserva = caixinhas.criar(ana.usuario(), ana.conta().getNumero(), "Reserva", null);

        assertThatThrownBy(() -> caixinhas.guardar(joao.usuario(), reserva.getId(), v("1")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(reserva.progresso()).isNull();
    }

    @Test
    void cartaoVirtualComNumeroValidoECvvQueMuda() {
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "0", "0");
        String n = ana.conta().getNumero();

        Cartao cartao = cartoes.cartao(ana.usuario(), n);
        assertThat(cartoes.cartao(ana.usuario(), n).getId()).isEqualTo(cartao.getId());
        assertThat(Luhn.valido(cartao.getNumero())).isTrue();
        assertThat(cartao.getNumero()).startsWith("529831");
        assertThat(cartao.getValidade()).isEqualTo("09/31");

        CartaoService.DadosSensiveis agora = cartoes.revelar(ana.usuario(), n);
        assertThat(agora.cvv()).matches("\\d{3}");
        assertThat(agora.cvvValidoAte()).isEqualTo(Instant.parse("2026-09-26T15:05:00Z"));
        assertThat(cartoes.revelar(ana.usuario(), n).cvv()).isEqualTo(agora.cvv());

        // Nas janelas seguintes o CVV muda (em ao menos uma de três, para não depender de coincidência).
        boolean mudou = false;
        for (int i = 1; i <= 3; i++) {
            relogio.ajustar(RelogioFixo.AGORA.plusSeconds(300L * i));
            mudou |= !cartoes.revelar(ana.usuario(), n).cvv().equals(agora.cvv());
        }
        assertThat(mudou).isTrue();

        cartoes.definirBloqueio(ana.usuario(), n, true);
        assertThat(cartoes.cartao(ana.usuario(), n).isBloqueado()).isTrue();
    }

    @Test
    void jurosDoChequeEspecialSaoCobradosUmaVezPorDia() {
        Cenario.Pessoa joao = cenario.cliente("Joao Silva", "0", "1000");
        Cenario.Pessoa ana = cenario.cliente("Ana Souza", "100", "0");
        contas.sacarEm(joao.conta().getNumero(), v("1000"), null, RelogioFixo.AGORA);

        LocalDate ontem = LocalDate.of(2026, 9, 25);
        assertThat(juros.cobrar(ontem)).isEqualTo(1);
        assertThat(juros.cobrar(ontem)).isZero();

        assertThat(joao.conta().getSaldo()).isEqualTo(v("-1002.67"));
        assertThat(ana.conta().getSaldo()).isEqualTo(v("100.00"));
        assertThat(lancamentos.existsByIdTransacao("JUROS-" + joao.conta().getNumero() + "-20260925")).isTrue();
        assertThat(contas.extrato(joao.usuario(), joao.conta().getNumero(), null, null).lancamentos())
                .extracting(l -> l.getTipo()).contains(TipoLancamento.JUROS_CHEQUE_ESPECIAL);
    }
}
