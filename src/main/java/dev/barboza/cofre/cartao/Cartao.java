package dev.barboza.cofre.cartao;

import java.time.Instant;

import dev.barboza.cofre.dominio.Conta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Cartão de débito virtual. O CVV não é guardado: é calculado na hora e muda a cada 5 minutos. */
@Entity
@Table(name = "cartao")
public class Cartao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "conta_id", unique = true)
    private Conta conta;

    @Column(nullable = false, unique = true, length = 16)
    private String numero;

    /** MM/AA. */
    @Column(nullable = false, length = 5)
    private String validade;

    @Column(nullable = false)
    private boolean bloqueado;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Cartao() {
    }

    public Cartao(Conta conta, String numero, String validade, Instant criadoEm) {
        this.conta = conta;
        this.numero = numero;
        this.validade = validade;
        this.criadoEm = criadoEm;
    }

    public void definirBloqueio(boolean bloqueado) {
        this.bloqueado = bloqueado;
    }

    public String finalDoNumero() {
        return numero.substring(12);
    }

    public Long getId() {
        return id;
    }

    public Conta getConta() {
        return conta;
    }

    public String getNumero() {
        return numero;
    }

    public String getValidade() {
        return validade;
    }

    public boolean isBloqueado() {
        return bloqueado;
    }
}
