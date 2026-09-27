package dev.barboza.cofre.pix;

import java.time.Instant;

import dev.barboza.cofre.dominio.Conta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "chave_pix")
public class ChavePix {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "conta_id")
    private Conta conta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoChavePix tipo;

    /** Valor normalizado: CPF só com dígitos, e-mail minúsculo, celular em +55..., aleatória em UUID. */
    @Column(nullable = false, unique = true, length = 77)
    private String valor;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    protected ChavePix() {
    }

    public ChavePix(Conta conta, TipoChavePix tipo, String valor, Instant criadaEm) {
        this.conta = conta;
        this.tipo = tipo;
        this.valor = valor;
        this.criadaEm = criadaEm;
    }

    public Long getId() {
        return id;
    }

    public Conta getConta() {
        return conta;
    }

    public TipoChavePix getTipo() {
        return tipo;
    }

    public String getValor() {
        return valor;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }
}
