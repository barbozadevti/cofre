package dev.barboza.cofre.dominio;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Linha do extrato. Imutável: só é criada pela {@link Conta}. */
@Entity
@Table(name = "lancamento", indexes = @Index(name = "ix_lancamento_conta_data", columnList = "conta_id, data_hora"))
public class Lancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_id")
    private Conta conta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoLancamento tipo;

    /** Sempre positivo; o sentido (entrada ou saída) vem do tipo. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "saldo_apos", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoApos;

    /** Número da outra conta, nas transferências. */
    @Column(length = 12)
    private String contraparte;

    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;

    protected Lancamento() {
    }

    Lancamento(Conta conta, TipoLancamento tipo, BigDecimal valor, BigDecimal saldoApos, String contraparte, Instant dataHora) {
        this.conta = conta;
        this.tipo = tipo;
        this.valor = valor;
        this.saldoApos = saldoApos;
        this.contraparte = contraparte;
        this.dataHora = dataHora;
    }

    /** Valor com sinal: positivo para entradas, negativo para saídas. */
    public BigDecimal valorComSinal() {
        return tipo.credito() ? valor : valor.negate();
    }

    public String descricao() {
        return switch (tipo) {
            case TRANSFERENCIA_ENVIADA -> "Transferência para " + contraparte;
            case TRANSFERENCIA_RECEBIDA -> "Transferência de " + contraparte;
            default -> tipo.descricao();
        };
    }

    public Long getId() {
        return id;
    }

    public Conta getConta() {
        return conta;
    }

    public TipoLancamento getTipo() {
        return tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public BigDecimal getSaldoApos() {
        return saldoApos;
    }

    public String getContraparte() {
        return contraparte;
    }

    public Instant getDataHora() {
        return dataHora;
    }
}
