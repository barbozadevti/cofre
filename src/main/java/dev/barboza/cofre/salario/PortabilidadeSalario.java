package dev.barboza.cofre.salario;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import dev.barboza.cofre.dominio.ContaCorrente;
import dev.barboza.cofre.dominio.Dinheiro;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Pedido de portabilidade de salário: o empregador continua pagando no banco da folha, que repassa
 * automaticamente para o Cofre. A situação não é gravada a cada mudança: é calculada pelo tempo desde
 * o pedido (simulação dos prazos do banco de origem). Só o cancelamento é gravado.
 */
@Entity
@Table(name = "portabilidade_salario")
public class PortabilidadeSalario {

    /** Prazos da simulação: o banco de origem recebe o pedido em 1 dia e conclui em 3. */
    public static final Duration ANALISE_COMECA = Duration.ofDays(1);
    public static final Duration CONCLUI_EM = Duration.ofDays(3);

    public enum Situacao {
        SOLICITADA("Pedido enviado"),
        EM_ANALISE("Em análise no banco de origem"),
        CONCLUIDA("Concluída"),
        CANCELADA("Cancelada");

        private final String nome;

        Situacao(String nome) {
            this.nome = nome;
        }

        public String nome() {
            return nome;
        }
    }

    public record Etapa(String nome, Instant quando, boolean feita) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, unique = true)
    private String protocolo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "conta_id")
    private ContaCorrente conta;

    @Column(name = "banco_origem", nullable = false, length = 3)
    private String bancoOrigem;

    @Column(nullable = false, length = 120)
    private String empregador;

    @Column(name = "cnpj_empregador", nullable = false, length = 14)
    private String cnpjEmpregador;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal salario;

    @Column(name = "dia_pagamento", nullable = false)
    private int diaPagamento;

    @Column(name = "solicitada_em", nullable = false)
    private Instant solicitadaEm;

    @Column(name = "cancelada_em")
    private Instant canceladaEm;

    protected PortabilidadeSalario() {
    }

    public PortabilidadeSalario(String protocolo, ContaCorrente conta, BancoDeOrigem banco, String empregador, String cnpj,
            BigDecimal salario, int diaPagamento, Instant quando) {
        String nome = empregador == null ? "" : empregador.trim().replaceAll("\\s+", " ");
        if (nome.length() < 2 || nome.length() > 120) {
            throw new OperacaoInvalidaException("Informe o nome do empregador.");
        }
        if (diaPagamento < 1 || diaPagamento > 31) {
            throw new OperacaoInvalidaException("O dia do pagamento vai de 1 a 31.");
        }
        conta.exigirAtiva();
        this.protocolo = protocolo;
        this.conta = conta;
        this.bancoOrigem = banco.codigo();
        this.empregador = nome;
        this.cnpjEmpregador = Cnpj.validar(cnpj);
        this.salario = Dinheiro.validarValor(salario);
        this.diaPagamento = diaPagamento;
        this.solicitadaEm = quando;
    }

    public Situacao situacao(Instant agora) {
        if (canceladaEm != null) {
            return Situacao.CANCELADA;
        }
        Duration passou = Duration.between(solicitadaEm, agora);
        if (passou.compareTo(CONCLUI_EM) >= 0) {
            return Situacao.CONCLUIDA;
        }
        return passou.compareTo(ANALISE_COMECA) >= 0 ? Situacao.EM_ANALISE : Situacao.SOLICITADA;
    }

    public Instant concluidaEm() {
        return solicitadaEm.plus(CONCLUI_EM);
    }

    /** Linha do tempo para a tela: o que já aconteceu e o que está previsto. */
    public List<Etapa> etapas(Instant agora) {
        if (canceladaEm != null) {
            return List.of(new Etapa("Pedido enviado", solicitadaEm, true), new Etapa("Cancelada", canceladaEm, true));
        }
        return List.of(
                new Etapa("Pedido enviado", solicitadaEm, true),
                new Etapa("Em análise no " + banco().nome(), solicitadaEm.plus(ANALISE_COMECA), !agora.isBefore(solicitadaEm.plus(ANALISE_COMECA))),
                new Etapa("Portabilidade concluída", concluidaEm(), !agora.isBefore(concluidaEm())));
    }

    /** O salário deste mês já pode cair no Cofre? (portabilidade concluída antes do dia do pagamento) */
    public boolean pagaEm(LocalDate dia, ZoneId fuso) {
        if (canceladaEm != null) {
            return false;
        }
        int diaNoMes = Math.min(diaPagamento, dia.lengthOfMonth());
        return dia.getDayOfMonth() == diaNoMes && !concluidaEm().atZone(fuso).toLocalDate().isAfter(dia);
    }

    public void cancelar(Instant agora) {
        if (situacao(agora) == Situacao.CONCLUIDA) {
            throw new OperacaoInvalidaException("A portabilidade já foi concluída. Para desfazer, peça ao banco de origem.");
        }
        if (canceladaEm != null) {
            throw new OperacaoInvalidaException("Esse pedido já foi cancelado.");
        }
        canceladaEm = agora;
    }

    public BancoDeOrigem banco() {
        return BancoDeOrigem.porCodigo(bancoOrigem);
    }

    public Long getId() {
        return id;
    }

    public String getProtocolo() {
        return protocolo;
    }

    public ContaCorrente getConta() {
        return conta;
    }

    public String getEmpregador() {
        return empregador;
    }

    public String getCnpjEmpregador() {
        return cnpjEmpregador;
    }

    public BigDecimal getSalario() {
        return salario;
    }

    public int getDiaPagamento() {
        return diaPagamento;
    }

    public Instant getSolicitadaEm() {
        return solicitadaEm;
    }
}
