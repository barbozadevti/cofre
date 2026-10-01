package dev.barboza.cofre.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Conta poupança: sem cheque especial (o saldo nunca fica negativo) e com rendimento mensal no
 * "aniversário", o dia do mês em que foi aberta, como na poupança de verdade.
 */
@Entity
@DiscriminatorValue("POUPANCA")
public class ContaPoupanca extends Conta {

    /**
     * 0,5% ao mês: a regra da poupança quando a Selic está acima de 8,5% ao ano (sem a TR, que é
     * praticamente zero). Simplificação deliberada, documentada no README.
     */
    public static final BigDecimal TAXA_MENSAL = new BigDecimal("0.005");

    protected ContaPoupanca() {
    }

    private ContaPoupanca(int numeroBase, String agencia, Cliente cliente, Instant abertaEm) {
        super(numeroBase, agencia, cliente, abertaEm);
    }

    public static Abertura<ContaPoupanca> abrir(int numeroBase, String agencia, Cliente cliente,
            BigDecimal depositoInicial, Instant quando) {
        return abertura(new ContaPoupanca(numeroBase, agencia, cliente, quando), depositoInicial, quando);
    }

    @Override
    public TipoConta getTipo() {
        return TipoConta.POUPANCA;
    }

    /** Poupança não tem cheque especial: por polimorfismo, o disponível é só o saldo. */
    @Override
    public BigDecimal getLimite() {
        return zero();
    }

    /**
     * Dia do mês em que a poupança rende. Contas abertas nos dias 29, 30 e 31 fazem aniversário no dia 1,
     * como determina a regra da poupança (nem todo mês tem esses dias).
     */
    public int diaDeAniversario(ZoneId fuso) {
        int dia = getAbertaEm().atZone(fuso).getDayOfMonth();
        return dia > 28 ? 1 : dia;
    }

    /**
     * Se {@code dia} é aniversário e já se passou pelo menos um mês da data-base. Aberta em 29, 30 ou 31,
     * a data-base é o dia 1 do mês seguinte (ex.: aberta em 30/03, primeiro rendimento em 01/05).
     */
    public boolean fazAniversario(LocalDate dia, ZoneId fuso) {
        LocalDate abertura = getAbertaEm().atZone(fuso).toLocalDate();
        LocalDate base = abertura.getDayOfMonth() > 28 ? abertura.plusMonths(1).withDayOfMonth(1) : abertura;
        return dia.getDayOfMonth() == diaDeAniversario(fuso) && !dia.isBefore(base.plusMonths(1));
    }

    public BigDecimal rendimentoEstimado() {
        return getSaldo().signum() > 0 ? getSaldo().multiply(TAXA_MENSAL).setScale(2, RoundingMode.HALF_EVEN) : zero();
    }

    /** Credita o rendimento do mês. Vazio se não houver o que render (saldo zero ou menos de R$ 0,01). */
    public Optional<Lancamento> render(String idTransacao, Instant quando) {
        exigirNaoEncerrada();
        BigDecimal rendimento = rendimentoEstimado();
        if (rendimento.signum() <= 0) {
            return Optional.empty();
        }
        String mensagem = "0,5% sobre " + Dinheiro.formatar(getSaldo());
        return Optional.of(creditar(TipoLancamento.RENDIMENTO_POUPANCA, rendimento, null, mensagem, idTransacao, quando));
    }
}
