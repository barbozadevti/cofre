package dev.barboza.cofre.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.barboza.cofre.caixinha.Caixinha;
import dev.barboza.cofre.cartao.Cartao;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.Cpf;
import dev.barboza.cofre.dominio.Lancamento;
import dev.barboza.cofre.dominio.SituacaoConta;
import dev.barboza.cofre.pix.ChavePix;
import dev.barboza.cofre.servico.Extrato;
import dev.barboza.cofre.servico.PainelService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Contratos de entrada e saída da API. */
public final class Dtos {

    private Dtos() {
    }

    // ---------- Entradas ----------

    public record ValorRequisicao(@NotNull(message = "Informe o valor.") BigDecimal valor) {
    }

    public record TransferenciaRequisicao(
            @NotBlank(message = "Informe a conta de origem.") String origem,
            @NotBlank(message = "Informe a conta de destino.") String destino,
            @NotNull(message = "Informe o valor.") BigDecimal valor,
            String mensagem) {
    }

    // ---------- Saídas ----------

    public record ContaResposta(
            String numero,
            String agencia,
            String titular,
            String cpfMascarado,
            BigDecimal saldo,
            BigDecimal limite,
            BigDecimal disponivel,
            BigDecimal usoDoLimite,
            SituacaoConta situacao,
            String motivoBloqueio,
            Instant abertaEm,
            Instant encerradaEm) {

        static ContaResposta de(Conta c) {
            return new ContaResposta(c.getNumero(), c.getAgencia(), c.getCliente().getNome(),
                    Cpf.mascarar(c.getCliente().getCpf()), c.getSaldo(), c.getLimite(), c.disponivel(), c.usoDoLimite(),
                    c.getSituacao(), c.getMotivoBloqueio(), c.getAbertaEm(), c.getEncerradaEm());
        }
    }

    public record LancamentoResposta(
            Instant dataHora,
            String tipo,
            String descricao,
            BigDecimal valor,
            BigDecimal saldoApos,
            String contraparte,
            String mensagem,
            String idTransacao) {

        static LancamentoResposta de(Lancamento l) {
            boolean caixinha = l.getTipo().name().startsWith("CAIXINHA");
            return new LancamentoResposta(l.getDataHora(), l.getTipo().name(), l.descricao(), l.valorComSinal(),
                    l.getSaldoApos(), l.getContraparte(), caixinha ? null : l.getMensagem(), l.getIdTransacao());
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

    public record ChaveResposta(Long id, String tipo, String tipoNome, String valor, String exibicao, Instant criadaEm) {

        static ChaveResposta de(ChavePix c) {
            String exibicao = switch (c.getTipo()) {
                case CPF -> Cpf.formatar(c.getValor());
                case TELEFONE -> "(" + c.getValor().substring(3, 5) + ") " + c.getValor().substring(5, 10) + "-"
                        + c.getValor().substring(10);
                default -> c.getValor();
            };
            return new ChaveResposta(c.getId(), c.getTipo().name(), c.getTipo().nome(), c.getValor(), exibicao, c.getCriadaEm());
        }
    }

    public record CaixinhaResposta(Long id, String conta, String nome, BigDecimal meta, BigDecimal saldo, Integer progresso) {

        static CaixinhaResposta de(Caixinha c) {
            return new CaixinhaResposta(c.getId(), c.getConta().getNumero(), c.getNome(), c.getMeta(), c.getSaldo(),
                    c.progresso());
        }
    }

    public record CartaoResposta(String conta, String nomeImpresso, String finalDoNumero, String validade, boolean bloqueado) {

        static CartaoResposta de(Cartao c) {
            String[] partes = c.getConta().getCliente().getNome().toUpperCase().split(" ");
            String nome = partes.length > 1 ? partes[0] + " " + partes[partes.length - 1] : partes[0];
            return new CartaoResposta(c.getConta().getNumero(), nome, c.finalDoNumero(), c.getValidade(), c.isBloqueado());
        }
    }

    public record PainelResposta(
            String nome,
            BigDecimal saldoTotal,
            BigDecimal limiteTotal,
            BigDecimal limiteEmUso,
            BigDecimal emCaixinhas,
            List<ContaResposta> contas,
            List<PainelService.Mes> meses) {
    }
}
