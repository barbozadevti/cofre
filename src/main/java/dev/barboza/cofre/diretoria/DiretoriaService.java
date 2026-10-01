package dev.barboza.cofre.diretoria;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.auditoria.AuditoriaRepository;
import dev.barboza.cofre.caixinha.CaixinhaRepository;
import dev.barboza.cofre.dominio.ClienteRepository;
import dev.barboza.cofre.dominio.ContaRepository;
import dev.barboza.cofre.dominio.Dinheiro;
import dev.barboza.cofre.dominio.LancamentoRepository;
import dev.barboza.cofre.dominio.SituacaoConta;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.salario.PortabilidadeRepository;
import dev.barboza.cofre.salario.PortabilidadeSalario;
import dev.barboza.cofre.seguranca.Perfil;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.dominio.AcessoNegadoException;
import dev.barboza.cofre.servico.ContaService;
import dev.barboza.cofre.servico.JurosChequeEspecial;

/**
 * Visão executiva (CEO): base de clientes, custódia, resultado de juros, carteira de cheque especial,
 * antifraude, Escudo de juros, portabilidade de salário e concentração. Só leitura.
 */
@Service
public class DiretoriaService {

    private final ClienteRepository clientes;
    private final ContaRepository contas;
    private final LancamentoRepository lancamentos;
    private final CaixinhaRepository caixinhas;
    private final AuditoriaRepository auditoria;
    private final PortabilidadeRepository portabilidades;
    private final Clock relogio;

    public DiretoriaService(ClienteRepository clientes, ContaRepository contas, LancamentoRepository lancamentos,
            CaixinhaRepository caixinhas, AuditoriaRepository auditoria, PortabilidadeRepository portabilidades, Clock relogio) {
        this.clientes = clientes;
        this.contas = contas;
        this.lancamentos = lancamentos;
        this.caixinhas = caixinhas;
        this.auditoria = auditoria;
        this.portabilidades = portabilidades;
        this.relogio = relogio;
    }

    public record Base(long clientes, long contasCorrentes, long poupancas, long contasBloqueadas) {
    }

    public record Custodia(BigDecimal contaCorrente, BigDecimal poupanca, BigDecimal caixinhas, BigDecimal total) {
    }

    public record Resultado(BigDecimal receitaDeJuros, BigDecimal custoDoRendimento, BigDecimal margem) {
    }

    public record Credito(BigDecimal limiteConcedido, BigDecimal emUso, BigDecimal utilizacao, long clientesNoNegativo) {
    }

    public record Pix(long quantidade, BigDecimal volume, BigDecimal ticketMedio) {
    }

    public record Antifraude(long retidos, long confirmados, long desistencias) {
    }

    public record Escudo(long contasProtegidas, long coberturas, BigDecimal valorCoberto, BigDecimal jurosRenunciados) {
    }

    public record Salario(long portabilidadesConcluidas, long emAndamento, BigDecimal folhaMensal, BigDecimal recebidoNoPeriodo) {
    }

    public record Concentracao(BigDecimal top5, List<Cliente> maiores) {
    }

    public record Cliente(String nome, BigDecimal saldo, BigDecimal participacao) {
    }

    public record Mes(String mes, BigDecimal receitaDeJuros, BigDecimal custoDoRendimento, BigDecimal volumePix,
            BigDecimal captacaoLiquida) {
    }

    public record Leitura(String tom, String texto) {
    }

    public record Painel(Base base, Custodia custodia, Resultado resultado, Credito credito, Pix pix, Antifraude antifraude,
            Escudo escudo, Salario salario, Concentracao concentracao, List<Mes> meses, List<Leitura> leituras) {
    }

    @Transactional(readOnly = true)
    public Painel painel(UsuarioLogado quem) {
        if (quem.perfil() != Perfil.DIRETORIA) {
            throw new AcessoNegadoException("Visão executiva disponível só para a diretoria.");
        }
        Instant agora = relogio.instant();
        Instant inicio30 = agora.minus(java.time.Duration.ofDays(30));

        Base base = new Base(clientes.count(), contas.countBySituacao(SituacaoConta.ATIVA) + contas.countBySituacao(SituacaoConta.BLOQUEADA)
                - contas.poupancasAtivas(), contas.poupancasAtivas(), contas.countBySituacao(SituacaoConta.BLOQUEADA));

        BigDecimal emCorrente = escala(contas.custodiaEmContaCorrente());
        BigDecimal emPoupanca = escala(contas.custodiaEmPoupanca());
        BigDecimal emCaixinhas = escala(caixinhas.totalGeral());
        Custodia custodia = new Custodia(emCorrente, emPoupanca, emCaixinhas, emCorrente.add(emPoupanca).add(emCaixinhas));

        BigDecimal juros = soma(TipoLancamento.JUROS_CHEQUE_ESPECIAL, inicio30, agora);
        BigDecimal rendimento = soma(TipoLancamento.RENDIMENTO_POUPANCA, inicio30, agora);
        Resultado resultado = new Resultado(juros, rendimento, juros.subtract(rendimento));

        BigDecimal limite = escala(contas.totalDeLimiteConcedido());
        BigDecimal uso = escala(contas.totalDeLimiteEmUso());
        Credito credito = new Credito(limite, uso, percentual(uso, limite), contas.correntesNoNegativo());

        long qtdPix = quantidade(TipoLancamento.PIX_ENVIADO, inicio30, agora);
        BigDecimal volumePix = soma(TipoLancamento.PIX_ENVIADO, inicio30, agora);
        Pix pix = new Pix(qtdPix, volumePix, qtdPix == 0 ? escala(BigDecimal.ZERO)
                : volumePix.divide(BigDecimal.valueOf(qtdPix), 2, RoundingMode.HALF_EVEN));

        long retidos = auditoria.countByAcaoAndDataHoraGreaterThanEqual("PIX_RETIDO_RISCO", inicio30);
        long confirmados = auditoria.countByAcaoAndDataHoraGreaterThanEqual("PIX_RISCO_CONFIRMADO", inicio30);
        Antifraude antifraude = new Antifraude(retidos, confirmados, Math.max(0, retidos - confirmados));

        BigDecimal coberto = soma(TipoLancamento.ESCUDO_COBERTURA, inicio30, agora);
        Escudo escudo = new Escudo(contas.contasComEscudo(), quantidade(TipoLancamento.ESCUDO_COBERTURA, inicio30, agora), coberto,
                coberto.multiply(JurosChequeEspecial.TAXA_MENSAL).setScale(2, RoundingMode.HALF_EVEN));

        List<PortabilidadeSalario> ativas = portabilidades.findByCanceladaEmIsNull();
        long concluidas = ativas.stream().filter(p -> p.situacao(agora) == PortabilidadeSalario.Situacao.CONCLUIDA).count();
        BigDecimal folha = ativas.stream().filter(p -> p.situacao(agora) == PortabilidadeSalario.Situacao.CONCLUIDA)
                .map(PortabilidadeSalario::getSalario).reduce(escala(BigDecimal.ZERO), BigDecimal::add);
        Salario salario = new Salario(concluidas, ativas.size() - concluidas, folha, soma(TipoLancamento.SALARIO_PORTADO, inicio30, agora));

        Concentracao concentracao = concentracao(custodia.total());
        List<Mes> meses = meses(agora);
        return new Painel(base, custodia, resultado, credito, pix, antifraude, escudo, salario, concentracao, meses,
                leituras(custodia, resultado, credito, antifraude, escudo, salario, concentracao));
    }

    private Concentracao concentracao(BigDecimal total) {
        List<Cliente> maiores = new ArrayList<>();
        BigDecimal top5 = escala(BigDecimal.ZERO);
        for (Object[] linha : contas.saldoPorCliente().stream().limit(5).toList()) {
            BigDecimal saldo = escala((BigDecimal) linha[1]);
            maiores.add(new Cliente((String) linha[0], saldo, percentual(saldo, total)));
            top5 = top5.add(saldo);
        }
        return new Concentracao(percentual(top5, total), maiores);
    }

    private List<Mes> meses(Instant agora) {
        YearMonth atual = YearMonth.from(agora.atZone(ContaService.FUSO));
        List<Mes> lista = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth m = atual.minusMonths(i);
            Instant de = m.atDay(1).atStartOfDay(ContaService.FUSO).toInstant();
            Instant ate = m.plusMonths(1).atDay(1).atStartOfDay(ContaService.FUSO).toInstant();
            BigDecimal entrou = soma(TipoLancamento.DEPOSITO, de, ate).add(soma(TipoLancamento.SALARIO_PORTADO, de, ate));
            BigDecimal saiu = soma(TipoLancamento.SAQUE, de, ate);
            lista.add(new Mes(m.toString(), soma(TipoLancamento.JUROS_CHEQUE_ESPECIAL, de, ate),
                    soma(TipoLancamento.RENDIMENTO_POUPANCA, de, ate), soma(TipoLancamento.PIX_ENVIADO, de, ate), entrou.subtract(saiu)));
        }
        return lista;
    }

    /** Frases curtas que um diretor leria primeiro: o que está bom, o que pede atenção. */
    private static List<Leitura> leituras(Custodia c, Resultado r, Credito cr, Antifraude a, Escudo e, Salario s, Concentracao k) {
        List<Leitura> l = new ArrayList<>();
        l.add(new Leitura("neutro", "Custódia de " + Dinheiro.formatar(c.total()) + ", com " + percentual(c.poupanca(), c.total())
                + "% em poupança."));
        l.add(new Leitura(r.margem().signum() >= 0 ? "bom" : "atencao", "Nos últimos 30 dias, os juros do cheque especial renderam "
                + Dinheiro.formatar(r.receitaDeJuros()) + " e a poupança custou " + Dinheiro.formatar(r.custoDoRendimento()) + "."));
        if (cr.utilizacao().compareTo(new BigDecimal("40")) > 0) {
            l.add(new Leitura("atencao", "Utilização do cheque especial em " + cr.utilizacao() + "% do limite concedido."));
        }
        if (e.valorCoberto().signum() > 0) {
            l.add(new Leitura("bom", "O Escudo cobriu " + Dinheiro.formatar(e.valorCoberto()) + " de saldo negativo: cerca de "
                    + Dinheiro.formatar(e.jurosRenunciados()) + " em juros a menos por mês, trocados por retenção."));
        }
        if (a.retidos() > 0) {
            l.add(new Leitura("bom", "O antifraude reteve " + a.retidos() + " Pix suspeito(s); " + a.desistencias()
                    + " não foram confirmados pelo cliente."));
        }
        if (s.portabilidadesConcluidas() > 0) {
            l.add(new Leitura("bom", s.portabilidadesConcluidas() + " cliente(s) já recebem o salário aqui: folha mensal de "
                    + Dinheiro.formatar(s.folhaMensal()) + "."));
        }
        if (k.top5().compareTo(new BigDecimal("50")) > 0) {
            l.add(new Leitura("atencao", "Concentração: os 5 maiores clientes têm " + k.top5() + "% da custódia."));
        }
        return l;
    }

    private BigDecimal soma(TipoLancamento tipo, Instant de, Instant ate) {
        return escala((BigDecimal) lancamentos.contarESomar(tipo, de, ate).getFirst()[1]);
    }

    private long quantidade(TipoLancamento tipo, Instant de, Instant ate) {
        return ((Number) lancamentos.contarESomar(tipo, de, ate).getFirst()[0]).longValue();
    }

    private static BigDecimal percentual(BigDecimal parte, BigDecimal todo) {
        if (todo.signum() == 0) {
            return escala(BigDecimal.ZERO);
        }
        return parte.multiply(BigDecimal.valueOf(100)).divide(todo, 1, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal escala(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_EVEN);
    }
}
