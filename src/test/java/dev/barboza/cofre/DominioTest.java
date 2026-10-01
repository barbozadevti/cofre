package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.barboza.cofre.dominio.Cliente;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.ContaCorrente;
import dev.barboza.cofre.dominio.Cpf;
import dev.barboza.cofre.dominio.Dinheiro;
import dev.barboza.cofre.dominio.IdTransacao;
import dev.barboza.cofre.dominio.Lancamento;
import dev.barboza.cofre.dominio.NumeroConta;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import dev.barboza.cofre.dominio.SaldoInsuficienteException;
import dev.barboza.cofre.dominio.SituacaoConta;
import dev.barboza.cofre.dominio.Telefone;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.servico.JurosChequeEspecial;

class DominioTest {

    private static final Instant AGORA = Instant.parse("2026-09-26T15:00:00Z");
    private static final Cliente MARIO = new Cliente("Mario Andrade", "52998224725", "mario@teste.dev", null, AGORA);

    private static BigDecimal v(String valor) {
        return new BigDecimal(valor);
    }

    private static ContaCorrente conta(String saldo, String limite) {
        ContaCorrente conta = ContaCorrente.abrir(10001, "0001", MARIO, v(saldo), AGORA).conta();
        conta.definirLimite(v(limite));
        return conta;
    }

    private static Lancamento sacar(Conta conta, String valor) {
        return conta.debitar(TipoLancamento.SAQUE, v(valor), null, null, "T1", AGORA, true);
    }

    @Nested
    class DinheiroTest {

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "0       | R$ 0,00",
                "237.48  | R$ 237,48",
                "1234.5  | R$ 1.234,50",
                "1000000 | R$ 1.000.000,00",
                "-10     | -R$ 10,00"})
        void formataNoPadraoBrasileiro(String valor, String esperado) {
            assertThat(Dinheiro.formatar(v(valor))).isEqualTo(esperado);
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {"150,75 | 150.75", "150.75 | 150.75", "1.234,56 | 1234.56", "R$ 1.234,56 | 1234.56"})
        void interpretaValoresDigitados(String texto, String esperado) {
            assertThat(Dinheiro.interpretar(texto)).isEqualByComparingTo(esperado);
        }

        @Test
        void validaValorDeOperacao() {
            assertThat(Dinheiro.validarValor(v("10.5"))).isEqualTo(v("10.50"));
            assertThatThrownBy(() -> Dinheiro.validarValor(v("0"))).hasMessage("O valor deve ser maior que zero.");
            assertThatThrownBy(() -> Dinheiro.validarValor(v("0.001"))).hasMessage("O valor deve ter no máximo 2 casas decimais.");
            assertThatThrownBy(() -> Dinheiro.validarValor(v("1000000.01"))).hasMessage("O valor máximo por operação é R$ 1.000.000,00.");
        }
    }

    @Nested
    class DocumentosTest {

        /** 529.982.247-25 é um CPF de exemplo conhecido; os demais foram conferidos por outra implementação. */
        @ParameterizedTest
        @CsvSource({"529982247, 52998224725", "111444777, 11144477735", "000000001, 00000000191"})
        void completaCpfComOsDigitos(String noveDigitos, String esperado) {
            assertThat(Cpf.completar(noveDigitos)).isEqualTo(esperado);
        }

        @Test
        void validaFormataEMascaraCpf() {
            assertThat(Cpf.validar("529.982.247-25")).isEqualTo("52998224725");
            assertThat(Cpf.formatar("52998224725")).isEqualTo("529.982.247-25");
            assertThat(Cpf.mascarar("52998224725")).isEqualTo("***.982.247-**");
            assertThatThrownBy(() -> Cpf.validar("529.982.247-26")).hasMessageContaining("dígitos verificadores");
            assertThatThrownBy(() -> Cpf.validar("111.111.111-11")).hasMessage("CPF inválido.");
            assertThatThrownBy(() -> Cpf.validar("123")).hasMessage("CPF inválido.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"(11) 98765-4321", "11987654321", "+55 11 98765-4321", "5511987654321"})
        void normalizaCelular(String digitado) {
            assertThat(Telefone.normalizar(digitado)).isEqualTo("+5511987654321");
        }

        @ParameterizedTest
        @ValueSource(strings = {"1187654321", "(11) 8765-4321", "01987654321", "abc"})
        void recusaCelularInvalido(String digitado) {
            assertThatThrownBy(() -> Telefone.normalizar(digitado)).isInstanceOf(OperacaoInvalidaException.class);
        }

        @ParameterizedTest
        @CsvSource({"10001, 10001-3", "10002, 10002-1", "10010, 10010-2", "1, 00001-9"})
        void numeroDaContaComDigitoModulo11(int base, String esperado) {
            assertThat(NumeroConta.formatar(base)).isEqualTo(esperado);
            assertThat(NumeroConta.normalizar(esperado.replace("-", ""))).isEqualTo(esperado);
        }

        @Test
        void idDeTransacaoNoFormatoEndToEndDoPix() {
            String id = IdTransacao.gerar('E', AGORA);
            assertThat(id).hasSize(32).startsWith("E31415926202609261500").matches("[A-Z0-9]{32}");
        }
    }

    @Nested
    class ChequeEspecialTest {

        @Test
        void sacaAlemDoSaldoAteOLimite() {
            ContaCorrente conta = conta("100", "500");

            sacar(conta, "600");

            assertThat(conta.getSaldo()).isEqualTo(v("-500.00"));
            assertThat(conta.usoDoLimite()).isEqualTo(v("500.00"));
            assertThat(conta.disponivel()).isEqualTo(v("0.00"));
        }

        @Test
        void naoPassaDoLimite() {
            ContaCorrente conta = conta("100", "500");

            assertThatThrownBy(() -> sacar(conta, "600.01"))
                    .isInstanceOf(SaldoInsuficienteException.class)
                    .hasMessage("Saldo insuficiente: disponível R$ 600,00 (com o cheque especial), solicitado R$ 600,01.");
            assertThat(conta.getSaldo()).isEqualTo(v("100.00"));
        }

        @Test
        void caixinhaNaoUsaOLimite() {
            ContaCorrente conta = conta("100", "500");

            assertThatThrownBy(() -> conta.debitar(TipoLancamento.CAIXINHA_GUARDADO, v("100.01"), null, "Viagem", "T1", AGORA, false))
                    .hasMessage("Saldo insuficiente: disponível R$ 100,00, solicitado R$ 100,01.");
        }

        @Test
        void limiteNaoPodeFicarAbaixoDoUso() {
            ContaCorrente conta = conta("0", "800");
            sacar(conta, "300");

            assertThatThrownBy(() -> conta.definirLimite(v("299.99")))
                    .hasMessage("O cliente está usando R$ 300,00 do cheque especial; o novo limite não pode ser menor que isso.");
            conta.definirLimite(v("300"));
            assertThat(conta.getLimite()).isEqualTo(v("300.00"));
            assertThatThrownBy(() -> conta.definirLimite(v("50000.01"))).hasMessage("O limite máximo do cheque especial é R$ 50.000,00.");
        }

        @Test
        void jurosDiariosDe8PorCentoAoMes() {
            // 1.000,00 negativos * 8% / 30 = 2,666... -> 2,67
            assertThat(JurosChequeEspecial.jurosDoDia(v("-1000.00"))).isEqualTo(v("2.67"));
            assertThat(JurosChequeEspecial.jurosDoDia(v("-412.37"))).isEqualTo(v("1.10"));
            assertThat(JurosChequeEspecial.jurosDoDia(v("-0.10"))).isEqualTo(v("0.00"));
        }

        @Test
        void jurosPodemPassarDoLimite() {
            ContaCorrente conta = conta("0", "100");
            sacar(conta, "100");
            conta.cobrarJuros(v("0.27"), "J1", null, AGORA);
            assertThat(conta.getSaldo()).isEqualTo(v("-100.27"));
        }
    }

    @Nested
    class SituacaoTest {

        @Test
        void bloqueadaRecebeMasNaoMovimentaSaidas() {
            ContaCorrente conta = conta("100", "0");
            conta.bloquear("Suspeita de fraude");

            conta.creditar(TipoLancamento.DEPOSITO, v("50"), null, null, "T1", AGORA);
            assertThat(conta.getSaldo()).isEqualTo(v("150.00"));
            assertThatThrownBy(() -> sacar(conta, "10"))
                    .hasMessage("A conta 10001-3 está bloqueada para saídas. Procure seu gerente.");

            conta.desbloquear();
            sacar(conta, "10");
            assertThat(conta.getSituacao()).isEqualTo(SituacaoConta.ATIVA);
        }

        @Test
        void bloqueioExigeMotivo() {
            ContaCorrente conta = conta("0", "0");
            assertThatThrownBy(() -> conta.bloquear("abc")).hasMessage("Informe o motivo do bloqueio (pelo menos 5 caracteres).");
            assertThatThrownBy(conta::desbloquear).hasMessage("A conta 10001-3 não está bloqueada.");
        }

        @Test
        void encerraSoComSaldoZero() {
            ContaCorrente conta = conta("20", "100");
            assertThatThrownBy(() -> conta.encerrar(AGORA))
                    .hasMessage("Para encerrar, o saldo precisa estar zerado. Saldo atual: R$ 20,00.");
            sacar(conta, "20");
            conta.encerrar(AGORA);

            assertThat(conta.getSituacao()).isEqualTo(SituacaoConta.ENCERRADA);
            assertThat(conta.getLimite()).isEqualTo(v("0.00"));
            assertThatThrownBy(() -> conta.creditar(TipoLancamento.DEPOSITO, v("1"), null, null, "T", AGORA))
                    .hasMessage("A conta 10001-3 está encerrada.");
        }

        @Test
        void mensagemDoDesafioOriginal() {
            assertThat(conta("237.48", "0").mensagemDeBoasVindas()).isEqualTo(
                    "Olá Mario Andrade, obrigado por criar uma conta em nosso banco, sua agência é 0001, "
                            + "conta 10001-3 e seu saldo R$ 237,48 já está disponível para saque.");
        }

        @Test
        void somaCentavosSemErroDeArredondamento() {
            ContaCorrente conta = conta("0", "0");
            for (int i = 0; i < 10; i++) {
                conta.creditar(TipoLancamento.DEPOSITO, v("0.10"), null, null, "T" + i, AGORA);
            }
            assertThat(conta.getSaldo()).isEqualTo(v("1.00"));
        }
    }
}
