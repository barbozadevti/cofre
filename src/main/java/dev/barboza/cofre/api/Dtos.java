package dev.barboza.cofre.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.Lancamento;
import dev.barboza.cofre.dominio.SituacaoConta;
import dev.barboza.cofre.servico.Extrato;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Contratos de entrada e saída da API. */
public final class Dtos {

    private Dtos() {
    }

    public record AberturaRequisicao(
            @NotBlank(message = "Informe o nome do titular.") String titular,
            String agencia,
            @NotNull(message = "Informe o saldo inicial (pode ser 0).") BigDecimal saldoInicial) {
    }

    public record ValorRequisicao(@NotNull(message = "Informe o valor.") BigDecimal valor) {
    }

    public record TransferenciaRequisicao(
            @NotBlank(message = "Informe a conta de origem.") String origem,
            @NotBlank(message = "Informe a conta de destino.") String destino,
            @NotNull(message = "Informe o valor.") BigDecimal valor) {
    }

    public record ContaResposta(
            String numero,
            String agencia,
            String titular,
            BigDecimal saldo,
            SituacaoConta situacao,
            Instant abertaEm,
            Instant encerradaEm) {

        static ContaResposta de(Conta conta) {
            return new ContaResposta(conta.getNumero(), conta.getAgencia(), conta.getTitular(), conta.getSaldo(),
                    conta.getSituacao(), conta.getAbertaEm(), conta.getEncerradaEm());
        }
    }

    public record AberturaResposta(ContaResposta conta, String mensagem) {
    }

    public record TransferenciaResposta(ContaResposta origem, ContaResposta destino) {
    }

    public record LancamentoResposta(
            Instant dataHora,
            String tipo,
            String descricao,
            BigDecimal valor,
            BigDecimal saldoApos,
            String contraparte) {

        static LancamentoResposta de(Lancamento l) {
            return new LancamentoResposta(l.getDataHora(), l.getTipo().name(), l.descricao(), l.valorComSinal(),
                    l.getSaldoApos(), l.getContraparte());
        }
    }

    public record ExtratoResposta(
            ContaResposta conta,
            LocalDate de,
            LocalDate ate,
            BigDecimal saldoInicial,
            BigDecimal entradas,
            BigDecimal saidas,
            BigDecimal saldoFinal,
            List<LancamentoResposta> lancamentos) {

        static ExtratoResposta de(Extrato e) {
            return new ExtratoResposta(ContaResposta.de(e.conta()), e.de(), e.ate(), e.saldoInicial(), e.entradas(),
                    e.saidas(), e.saldoFinal(), e.lancamentos().stream().map(LancamentoResposta::de).toList());
        }
    }
}
