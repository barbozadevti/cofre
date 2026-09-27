package dev.barboza.cofre.servico;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.ContaNaoEncontradaException;
import dev.barboza.cofre.dominio.ContaRepository;
import dev.barboza.cofre.dominio.Lancamento;
import dev.barboza.cofre.dominio.LancamentoRepository;
import dev.barboza.cofre.dominio.NumeroConta;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;

/** Casos de uso do Cofre. Site, API e terminal passam todos por aqui. */
@Service
public class ContaService {

    /** Fuso usado para "dia" no extrato. */
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    /** Primeiro número de conta emitido. */
    static final int PRIMEIRO_NUMERO = 10001;

    private final ContaRepository contas;
    private final LancamentoRepository lancamentos;
    private final Clock relogio;

    public ContaService(ContaRepository contas, LancamentoRepository lancamentos, Clock relogio) {
        this.contas = contas;
        this.lancamentos = lancamentos;
        this.relogio = relogio;
    }

    @Transactional
    public Conta abrir(String titular, String agencia, BigDecimal saldoInicial) {
        return abrirEm(titular, agencia, saldoInicial, relogio.instant());
    }

    /** Abre com data informada; usado pelos dados de demonstração. */
    @Transactional
    public Conta abrirEm(String titular, String agencia, BigDecimal saldoInicial, Instant quando) {
        int proximo = Math.max(contas.maiorNumeroBase() + 1, PRIMEIRO_NUMERO);
        Conta.Abertura abertura = Conta.abrir(proximo, agencia, titular, saldoInicial, quando);
        Conta conta = contas.save(abertura.conta());
        lancamentos.save(abertura.lancamento());
        return conta;
    }

    @Transactional(readOnly = true)
    public List<Conta> listar() {
        return contas.findAllByOrderByNumeroBaseAsc();
    }

    @Transactional(readOnly = true)
    public Conta buscar(String numero) {
        String normalizado = NumeroConta.normalizar(numero);
        return contas.findByNumero(normalizado).orElseThrow(() -> new ContaNaoEncontradaException(normalizado));
    }

    @Transactional
    public Conta depositar(String numero, BigDecimal valor) {
        return depositarEm(numero, valor, relogio.instant());
    }

    @Transactional
    public Conta depositarEm(String numero, BigDecimal valor, Instant quando) {
        Conta conta = buscar(numero);
        lancamentos.save(conta.depositar(valor, quando));
        return conta;
    }

    @Transactional
    public Conta sacar(String numero, BigDecimal valor) {
        return sacarEm(numero, valor, relogio.instant());
    }

    @Transactional
    public Conta sacarEm(String numero, BigDecimal valor, Instant quando) {
        Conta conta = buscar(numero);
        lancamentos.save(conta.sacar(valor, quando));
        return conta;
    }

    /** Transferência atômica: ou as duas contas mudam, ou nenhuma muda. */
    @Transactional
    public Transferencia transferir(String origem, String destino, BigDecimal valor) {
        return transferirEm(origem, destino, valor, relogio.instant());
    }

    @Transactional
    public Transferencia transferirEm(String origem, String destino, BigDecimal valor, Instant quando) {
        Conta contaOrigem = buscar(origem);
        Conta contaDestino = buscar(destino);
        lancamentos.saveAll(List.of(contaOrigem.transferir(contaDestino, valor, quando)));
        return new Transferencia(contaOrigem, contaDestino);
    }

    public record Transferencia(Conta origem, Conta destino) {
    }

    @Transactional
    public Conta encerrar(String numero) {
        Conta conta = buscar(numero);
        conta.encerrar(relogio.instant());
        return conta;
    }

    /** Extrato de {@code de} até {@code ate} (inclusive). Sem datas, mostra os últimos 30 dias. */
    @Transactional(readOnly = true)
    public Extrato extrato(String numero, LocalDate de, LocalDate ate) {
        Conta conta = buscar(numero);
        LocalDate hoje = LocalDate.now(relogio.withZone(FUSO));
        LocalDate fim = ate != null ? ate : hoje;
        LocalDate inicio = de != null ? de : fim.minusDays(29);
        if (inicio.isAfter(fim)) {
            throw new OperacaoInvalidaException("A data inicial não pode ser depois da data final.");
        }
        Instant desde = inicio.atStartOfDay(FUSO).toInstant();
        Instant ateExclusivo = fim.plusDays(1).atStartOfDay(FUSO).toInstant();

        BigDecimal saldoInicial = lancamentos
                .findFirstByContaIdAndDataHoraLessThanOrderByDataHoraDescIdDesc(conta.getId(), desde)
                .map(Lancamento::getSaldoApos)
                .orElse(BigDecimal.ZERO.setScale(2));
        List<Lancamento> doPeriodo = lancamentos
                .findByContaIdAndDataHoraGreaterThanEqualAndDataHoraLessThanOrderByDataHoraAscIdAsc(
                        conta.getId(), desde, ateExclusivo);
        return new Extrato(conta, inicio, fim, saldoInicial, doPeriodo);
    }
}
