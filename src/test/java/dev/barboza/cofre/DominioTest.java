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

import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.Dinheiro;
import dev.barboza.cofre.dominio.Lancamento;
import dev.barboza.cofre.dominio.NumeroConta;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import dev.barboza.cofre.dominio.SaldoInsuficienteException;
import dev.barboza.cofre.dominio.SituacaoConta;
import dev.barboza.cofre.dominio.TipoLancamento;

class DominioTest {

    private static final Instant AGORA = Instant.parse("2026-09-26T15:00:00Z");

    private static BigDecimal v(String valor) {
        return new BigDecimal(valor);
    }

    private static Conta conta(int base, String saldo) {
        return Conta.abrir(base, "0001", "Mario Andrade", v(saldo), AGORA).conta();
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
        @CsvSource(delimiter = '|', value = {
                "150,75      | 150.75",
                "150.75      | 150.75",
                "1.234,56    | 1234.56",
                "R$ 1.234,56 | 1234.56",
                "  10  | 10"})
        void interpretaValoresDigitados(String texto, String esperado) {
            assertThat(Dinheiro.interpretar(texto)).isEqualByComparingTo(esperado);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "abc", "10,5,3", "R$"})
        void recusaTextoQueNaoEValor(String texto) {
            assertThatThrownBy(() -> Dinheiro.interpretar(texto)).isInstanceOf(OperacaoInvalidaException.class);
        }

        @Test
        void validaValorDeOperacao() {
            assertThat(Dinheiro.validarValor(v("10.5"))).isEqualTo(v("10.50"));
            assertThatThrownBy(() -> Dinheiro.validarValor(null)).hasMessage("Informe o valor.");
            assertThatThrownBy(() -> Dinheiro.validarValor(v("0"))).hasMessage("O valor deve ser maior que zero.");
            assertThatThrownBy(() -> Dinheiro.validarValor(v("-1"))).hasMessage("O valor deve ser maior que zero.");
            assertThatThrownBy(() -> Dinheiro.validarValor(v("0.001")))
                    .hasMessage("O valor deve ter no máximo 2 casas decimais.");
            assertThatThrownBy(() -> Dinheiro.validarValor(v("1000000.01")))
                    .hasMessage("O valor máximo por operação é R$ 1.000.000,00.");
            assertThat(Dinheiro.validarValor(v("1000000.00"))).isEqualTo(v("1000000.00"));
            assertThat(Dinheiro.validarValor(v("5.000"))).isEqualTo(v("5.00"));
        }

        @Test
        void saldoInicialPodeSerZeroMasNaoNegativo() {
            assertThat(Dinheiro.validarValorOuZero(v("0"))).isEqualTo(v("0.00"));
            assertThatThrownBy(() -> Dinheiro.validarValorOuZero(v("-0.01")))
                    .hasMessage("O valor não pode ser negativo.");
            assertThatThrownBy(() -> Dinheiro.validarValorOuZero(null)).hasMessage("Informe o valor.");
        }
    }

    @Nested
    class NumeroContaTest {

        @ParameterizedTest
        @CsvSource({"10001, 10001-3", "10002, 10002-1", "10010, 10010-2", "1, 00001-9", "123456, 123456-0"})
        void geraDigitoPeloModulo11(int base, String esperado) {
            assertThat(NumeroConta.formatar(base)).isEqualTo(esperado);
        }

        @ParameterizedTest
        @ValueSource(strings = {"10001-3", "100013", " 10001-3 ", "10001.3"})
        void normalizaComOuSemHifen(String digitado) {
            assertThat(NumeroConta.normalizar(digitado)).isEqualTo("10001-3");
        }

        @Test
        void recusaDigitoErrado() {
            assertThatThrownBy(() -> NumeroConta.normalizar("10001-5"))
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessage("Número de conta inválido: o dígito verificador de 10001-5 não confere.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "7", "abc", "1234567890"})
        void recusaFormatoInvalido(String digitado) {
            assertThatThrownBy(() -> NumeroConta.normalizar(digitado))
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessageStartingWith("Número de conta inválido");
        }
    }

    @Nested
    class ContaTest {

        @Test
        void abreComLancamentoDeAberturaEMensagemDoDesafio() {
            Conta.Abertura abertura = Conta.abrir(10001, "0678", "  Mario   Andrade ", v("237.48"), AGORA);

            Conta conta = abertura.conta();
            assertThat(conta.getNumero()).isEqualTo("10001-3");
            assertThat(conta.getTitular()).isEqualTo("Mario Andrade");
            assertThat(conta.getSaldo()).isEqualTo(v("237.48"));
            assertThat(conta.getSituacao()).isEqualTo(SituacaoConta.ATIVA);
            assertThat(abertura.lancamento().getTipo()).isEqualTo(TipoLancamento.ABERTURA);
            assertThat(abertura.lancamento().getSaldoApos()).isEqualTo(v("237.48"));
            assertThat(conta.mensagemDeBoasVindas()).isEqualTo(
                    "Olá Mario Andrade, obrigado por criar uma conta em nosso banco, sua agência é 0678, "
                            + "conta 10001-3 e seu saldo R$ 237,48 já está disponível para saque.");
        }

        @Test
        void agenciaEmBrancoViraPadrao() {
            Conta conta = Conta.abrir(10001, " ", "Ana Souza", v("0"), AGORA).conta();
            assertThat(conta.getAgencia()).isEqualTo("0001");
            assertThat(conta.getSaldo()).isEqualTo(v("0.00"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"1", "12345", "ab12", "067-8"})
        void recusaAgenciaSemQuatroDigitos(String agencia) {
            assertThatThrownBy(() -> Conta.abrir(10001, agencia, "Ana Souza", v("0"), AGORA))
                    .hasMessage("A agência deve ter 4 dígitos, por exemplo 0001.");
        }

        @Test
        void validaNomeDoTitular() {
            assertThatThrownBy(() -> Conta.abrir(10001, null, "Al", v("0"), AGORA))
                    .hasMessage("Informe o nome do titular (pelo menos 3 letras).");
            assertThatThrownBy(() -> Conta.abrir(10001, null, null, v("0"), AGORA))
                    .hasMessage("Informe o nome do titular (pelo menos 3 letras).");
            assertThatThrownBy(() -> Conta.abrir(10001, null, "Ana 123", v("0"), AGORA))
                    .hasMessage("O nome do titular deve ter apenas letras.");
            assertThatThrownBy(() -> Conta.abrir(10001, null, "A".repeat(81), v("0"), AGORA))
                    .hasMessage("O nome do titular pode ter no máximo 80 caracteres.");
            assertThat(Conta.abrir(10001, null, "Joana D'Arc-Lima", v("0"), AGORA).conta().getTitular())
                    .isEqualTo("Joana D'Arc-Lima");
        }

        @Test
        void depositaESaca() {
            Conta conta = conta(10001, "100");

            Lancamento deposito = conta.depositar(v("50.25"), AGORA);
            Lancamento saque = conta.sacar(v("150.25"), AGORA);

            assertThat(deposito.getSaldoApos()).isEqualTo(v("150.25"));
            assertThat(deposito.valorComSinal()).isEqualTo(v("50.25"));
            assertThat(saque.getSaldoApos()).isEqualTo(v("0.00"));
            assertThat(saque.valorComSinal()).isEqualTo(v("-150.25"));
            assertThat(conta.getSaldo()).isEqualTo(v("0.00"));
        }

        @Test
        void naoSacaMaisQueOSaldo() {
            Conta conta = conta(10001, "80");

            assertThatThrownBy(() -> conta.sacar(v("100"), AGORA))
                    .isInstanceOf(SaldoInsuficienteException.class)
                    .hasMessage("Saldo insuficiente: disponível R$ 80,00, solicitado R$ 100,00.");
            assertThat(conta.getSaldo()).isEqualTo(v("80.00"));
        }

        @Test
        void somaCentavosSemErroDeArredondamento() {
            Conta conta = conta(10001, "0");
            for (int i = 0; i < 10; i++) {
                conta.depositar(v("0.10"), AGORA);
            }
            assertThat(conta.getSaldo()).isEqualTo(v("1.00"));
        }

        @Test
        void transfereEntreContas() {
            Conta origem = conta(10001, "500");
            Conta destino = conta(10002, "10");

            Lancamento[] lancamentos = origem.transferir(destino, v("200"), AGORA);

            assertThat(origem.getSaldo()).isEqualTo(v("300.00"));
            assertThat(destino.getSaldo()).isEqualTo(v("210.00"));
            assertThat(lancamentos[0].descricao()).isEqualTo("Transferência para 10002-1");
            assertThat(lancamentos[1].descricao()).isEqualTo("Transferência de 10001-3");
        }

        @Test
        void transferenciaSemSaldoNaoMexeEmNenhumaConta() {
            Conta origem = conta(10001, "50");
            Conta destino = conta(10002, "10");

            assertThatThrownBy(() -> origem.transferir(destino, v("51"), AGORA))
                    .isInstanceOf(SaldoInsuficienteException.class);
            assertThat(origem.getSaldo()).isEqualTo(v("50.00"));
            assertThat(destino.getSaldo()).isEqualTo(v("10.00"));
        }

        @Test
        void naoTransfereParaAPropriaConta() {
            Conta conta = conta(10001, "50");
            assertThatThrownBy(() -> conta.transferir(conta, v("1"), AGORA))
                    .hasMessage("A conta de destino deve ser diferente da conta de origem.");
        }

        @Test
        void encerraSoComSaldoZeroEDepoisNaoMovimenta() {
            Conta conta = conta(10001, "20");

            assertThatThrownBy(() -> conta.encerrar(AGORA))
                    .hasMessage("Para encerrar, o saldo precisa estar zerado. Saldo atual: R$ 20,00.");

            conta.sacar(v("20"), AGORA);
            conta.encerrar(AGORA);

            assertThat(conta.getSituacao()).isEqualTo(SituacaoConta.ENCERRADA);
            assertThat(conta.getEncerradaEm()).isEqualTo(AGORA);
            assertThatThrownBy(() -> conta.depositar(v("1"), AGORA)).hasMessage("A conta 10001-3 está encerrada.");
            assertThatThrownBy(() -> conta.encerrar(AGORA)).hasMessage("A conta 10001-3 está encerrada.");
        }

        @Test
        void naoTransfereParaContaEncerrada() {
            Conta origem = conta(10001, "50");
            Conta destino = conta(10002, "0");
            destino.encerrar(AGORA);

            assertThatThrownBy(() -> origem.transferir(destino, v("10"), AGORA))
                    .hasMessage("A conta 10002-1 está encerrada.");
            assertThat(origem.getSaldo()).isEqualTo(v("50.00"));
        }
    }
}
