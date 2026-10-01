package dev.barboza.cofre.copiloto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.auditoria.Auditoria;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.ContaCorrente;
import dev.barboza.cofre.dominio.ContaPoupanca;
import dev.barboza.cofre.dominio.ContaRepository;
import dev.barboza.cofre.dominio.Dinheiro;
import dev.barboza.cofre.dominio.Lancamento;
import dev.barboza.cofre.dominio.LancamentoRepository;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.servico.Acesso;
import dev.barboza.cofre.servico.ContaService;
import dev.barboza.cofre.servico.JurosChequeEspecial;

/**
 * Copiloto financeiro: prevê o saldo dos próximos 30 dias e, com o Escudo de juros ligado, cobre o saldo
 * negativo com a poupança do próprio cliente antes de qualquer juro.
 * <p>
 * Para o banco, o Escudo troca uma receita de juros por retenção: o cliente que nunca paga 8% ao mês
 * tendo dinheiro parado na poupança não tem motivo para mudar de banco.
 */
@Service
public class CopilotoService {

    /** O que o cliente deixa de perder por real coberto em um mês: 8% de juros menos 0,5% que a poupança renderia. */
    public static final BigDecimal ECONOMIA_POR_REAL = JurosChequeEspecial.TAXA_MENSAL.subtract(ContaPoupanca.TAXA_MENSAL);

    /** Movimentos que não dizem nada sobre o dia a dia do cliente. */
    private static final Set<TipoLancamento> FORA_DA_PREVISAO = EnumSet.of(TipoLancamento.ABERTURA,
            TipoLancamento.ESCUDO_RESGATE, TipoLancamento.ESCUDO_COBERTURA, TipoLancamento.JUROS_CHEQUE_ESPECIAL);
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    private final ContaService contas;
    private final ContaRepository repositorio;
    private final LancamentoRepository lancamentos;
    private final Auditoria auditoria;
    private final Clock relogio;

    public CopilotoService(ContaService contas, ContaRepository repositorio, LancamentoRepository lancamentos,
            Auditoria auditoria, Clock relogio) {
        this.contas = contas;
        this.repositorio = repositorio;
        this.lancamentos = lancamentos;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    public record Escudo(boolean disponivel, boolean ativo, Instant desde, String poupanca, BigDecimal saldoDaPoupanca,
            long coberturas, BigDecimal totalCoberto, BigDecimal economiaEstimada) {
    }

    public enum Nivel { TRANQUILO, ATENCAO, PROTEGIDO }

    public record Copiloto(PrevisaoDeSaldo.Resultado previsao, Escudo escudo, Nivel nivel, String titulo, String mensagem) {
    }

    @Transactional(readOnly = true)
    public Copiloto copiloto(UsuarioLogado quem, String numero) {
        Conta conta = contas.buscar(quem, numero);
        LocalDate hoje = LocalDate.now(relogio.withZone(ContaService.FUSO));
        Instant desde = hoje.minusMonths(PrevisaoDeSaldo.MESES_DE_HISTORICO + 1L).withDayOfMonth(1)
                .atStartOfDay(ContaService.FUSO).toInstant();
        List<PrevisaoDeSaldo.Movimento> historico = lancamentos
                .findByContaIdInAndDataHoraGreaterThanEqualOrderByDataHoraAsc(List.of(conta.getId()), desde).stream()
                .filter(l -> !FORA_DA_PREVISAO.contains(l.getTipo()))
                .map(CopilotoService::movimento)
                .toList();
        PrevisaoDeSaldo.Resultado previsao = PrevisaoDeSaldo.prever(historico, conta.getSaldo(), hoje);
        Escudo escudo = escudo(conta);
        return explicar(previsao, escudo);
    }

    /** Descrição usada para reconhecer o que se repete: a mensagem e quem está do outro lado. */
    static PrevisaoDeSaldo.Movimento movimento(Lancamento l) {
        String descricao = l.getMensagem() != null && l.getContraparteNome() != null
                ? l.getMensagem() + " · " + l.getContraparteNome()
                : l.getMensagem() != null ? l.getMensagem()
                : l.getContraparteNome() != null ? l.getTipo().descricao() + " · " + l.getContraparteNome()
                : l.getTipo().descricao();
        BigDecimal valor = l.getTipo().credito() ? l.getValor() : l.getValor().negate();
        return new PrevisaoDeSaldo.Movimento(l.getDataHora().atZone(ContaService.FUSO).toLocalDate(), descricao, valor);
    }

    private Copiloto explicar(PrevisaoDeSaldo.Resultado p, Escudo e) {
        if (!p.vaiFicarNegativo()) {
            return new Copiloto(p, e, Nivel.TRANQUILO, "Tudo certo nos próximos 30 dias",
                    "O menor saldo previsto é " + Dinheiro.formatar(p.menorSaldo()) + " em " + DIA.format(p.diaDoMenorSaldo()) + ".");
        }
        String quando = "Em " + DIA.format(p.primeiroDiaNegativo()) + " sua conta deve ficar negativa"
                + causa(p) + ", chegando a " + Dinheiro.formatar(p.menorSaldo()) + " em " + DIA.format(p.diaDoMenorSaldo()) + ".";
        BigDecimal falta = p.menorSaldo().negate();
        if (e.ativo() && e.saldoDaPoupanca().compareTo(falta) >= 0) {
            return new Copiloto(p, e, Nivel.PROTEGIDO, "O Escudo vai cobrir", quando
                    + " O Escudo cobre com a sua poupança, sem juros. Economia estimada: "
                    + Dinheiro.formatar(falta.multiply(ECONOMIA_POR_REAL).setScale(2, RoundingMode.HALF_EVEN)) + " por mês.");
        }
        String sugestao = !e.disponivel() ? " Abra uma poupança e ligue o Escudo: o saldo negativo passa a ser coberto sem juros."
                : !e.ativo() ? " Ligue o Escudo para cobrir com a poupança e evitar juros de 8% ao mês."
                : " A poupança cobre só " + Dinheiro.formatar(e.saldoDaPoupanca()) + ": vale adiar algum gasto.";
        return new Copiloto(p, e, Nivel.ATENCAO, "Atenção ao saldo", quando + sugestao);
    }

    private static String causa(PrevisaoDeSaldo.Resultado p) {
        return p.eventos().stream()
                .filter(ev -> ev.dia().equals(p.primeiroDiaNegativo()) && ev.valor().signum() < 0)
                .findFirst()
                .map(ev -> " por causa de \"" + ev.descricao().split(" · ")[0] + "\" (" + Dinheiro.formatar(ev.valor().negate()) + ")")
                .orElse("");
    }

    private Escudo escudo(Conta conta) {
        if (!(conta instanceof ContaCorrente corrente)) {
            return new Escudo(false, false, null, null, BigDecimal.ZERO.setScale(2), 0, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
        }
        List<ContaPoupanca> poupancas = repositorio.poupancasAtivasDoCliente(corrente.getCliente().getId());
        BigDecimal saldoPoupanca = poupancas.stream().map(Conta::getSaldo).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        long coberturas = lancamentos.countByContaIdAndTipo(corrente.getId(), TipoLancamento.ESCUDO_COBERTURA);
        BigDecimal total = lancamentos.somar(corrente.getId(), List.of(TipoLancamento.ESCUDO_COBERTURA), Instant.EPOCH,
                Instant.parse("9999-12-31T00:00:00Z")).setScale(2);
        return new Escudo(!poupancas.isEmpty(), corrente.isEscudoAtivo(), corrente.getEscudoDesde(),
                poupancas.isEmpty() ? null : poupancas.getFirst().getNumero(), saldoPoupanca, coberturas, total,
                total.multiply(ECONOMIA_POR_REAL).setScale(2, RoundingMode.HALF_EVEN));
    }

    /** Liga o Escudo e já cobre o saldo negativo atual, se houver. */
    @Transactional
    public Copiloto ativarEscudo(UsuarioLogado quem, String numero) {
        ContaCorrente corrente = corrente(quem, numero);
        if (repositorio.poupancasAtivasDoCliente(corrente.getCliente().getId()).isEmpty()) {
            throw new OperacaoInvalidaException("Abra uma poupança para usar o Escudo de juros.");
        }
        Instant agora = relogio.instant();
        corrente.ativarEscudo(agora);
        BigDecimal coberto = contas.cobrirComEscudo(corrente, agora, null);
        auditoria.registrar(quem, "ESCUDO_ATIVADO", "Conta " + corrente.getNumero()
                + (coberto.signum() > 0 ? "; cobriu " + Dinheiro.formatar(coberto) + " na hora" : ""));
        return copiloto(quem, numero);
    }

    @Transactional
    public Copiloto desativarEscudo(UsuarioLogado quem, String numero) {
        ContaCorrente corrente = corrente(quem, numero);
        corrente.desativarEscudo();
        auditoria.registrar(quem, "ESCUDO_DESATIVADO", "Conta " + corrente.getNumero());
        return copiloto(quem, numero);
    }

    /** Rotina diária, antes dos juros: cobre quem ficou negativo por outros motivos (ex.: tarifa, juros). */
    @Transactional
    public int proteger(Instant quando) {
        int cobertas = 0;
        for (ContaCorrente c : repositorio.comEscudoNoNegativo()) {
            if (contas.cobrirComEscudo(c, quando, null).signum() > 0) {
                cobertas++;
            }
        }
        return cobertas;
    }

    private ContaCorrente corrente(UsuarioLogado quem, String numero) {
        Conta conta = contas.buscarInterno(numero);
        Acesso.exigirDono(quem, conta);
        if (!(conta instanceof ContaCorrente corrente)) {
            throw new OperacaoInvalidaException("O Escudo de juros protege a conta corrente.");
        }
        return corrente;
    }
}
