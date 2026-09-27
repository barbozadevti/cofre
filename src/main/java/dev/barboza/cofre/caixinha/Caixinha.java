package dev.barboza.cofre.caixinha;

import java.math.BigDecimal;
import java.time.Instant;

import dev.barboza.cofre.dominio.Conta;
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
import jakarta.persistence.Version;

/** Dinheiro separado por objetivo ("Viagem", "Reserva"). Sai do saldo da conta e volta no resgate. */
@Entity
@Table(name = "caixinha")
public class Caixinha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "conta_id")
    private Conta conta;

    @Column(nullable = false, length = 40)
    private String nome;

    @Column(precision = 15, scale = 2)
    private BigDecimal meta;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Version
    private long versao;

    protected Caixinha() {
    }

    public Caixinha(Conta conta, String nome, BigDecimal meta, Instant criadaEm) {
        String limpo = nome == null ? "" : nome.trim().replaceAll("\\s+", " ");
        if (limpo.length() < 2 || limpo.length() > 40) {
            throw new OperacaoInvalidaException("O nome da caixinha deve ter de 2 a 40 caracteres.");
        }
        this.conta = conta;
        this.nome = limpo;
        this.meta = meta == null || meta.signum() == 0 ? null : Dinheiro.validarValor(meta);
        this.saldo = BigDecimal.ZERO.setScale(2);
        this.criadaEm = criadaEm;
    }

    void somar(BigDecimal valor) {
        saldo = saldo.add(valor);
    }

    void retirar(BigDecimal valor) {
        if (saldo.compareTo(valor) < 0) {
            throw new OperacaoInvalidaException("A caixinha " + nome + " tem só " + Dinheiro.formatar(saldo) + ".");
        }
        saldo = saldo.subtract(valor);
    }

    /** Percentual da meta atingido (0 a 100), ou nulo sem meta. */
    public Integer progresso() {
        if (meta == null) {
            return null;
        }
        return saldo.multiply(BigDecimal.valueOf(100)).divide(meta, 0, java.math.RoundingMode.DOWN).min(BigDecimal.valueOf(100)).intValue();
    }

    public Long getId() {
        return id;
    }

    public Conta getConta() {
        return conta;
    }

    public String getNome() {
        return nome;
    }

    public BigDecimal getMeta() {
        return meta;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }
}
