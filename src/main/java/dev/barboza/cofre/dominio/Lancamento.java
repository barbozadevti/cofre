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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Linha do extrato. Imutável: só é criada pela {@link Conta}. */
@Entity
@Table(name = "lancamento")
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

    /** Número da outra conta, nas transferências e no Pix. */
    @Column(length = 12)
    private String contraparte;

    @Column(name = "contraparte_nome", length = 80)
    private String contraparteNome;

    @Column(length = 140)
    private String mensagem;

    /** Identificador da transação: os dois lados de uma transferência compartilham o mesmo. */
    @Column(name = "id_transacao", nullable = false, length = 40)
    private String idTransacao;

    /** Chave enviada pelo cliente no cabeçalho Idempotency-Key, para não duplicar um Pix. */
    @Column(name = "chave_idempotencia", length = 64)
    private String chaveIdempotencia;

    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;

    protected Lancamento() {
    }

    Lancamento(Conta conta, TipoLancamento tipo, BigDecimal valor, BigDecimal saldoApos, String contraparte,
            String contraparteNome, String mensagem, String idTransacao, Instant dataHora) {
        this.conta = conta;
        this.tipo = tipo;
        this.valor = valor;
        this.saldoApos = saldoApos;
        this.contraparte = contraparte;
        this.contraparteNome = contraparteNome;
        this.mensagem = mensagem;
        this.idTransacao = idTransacao;
        this.dataHora = dataHora;
    }

    public void marcarIdempotencia(String chave) {
        this.chaveIdempotencia = chave;
    }

    /** Valor com sinal: positivo para entradas, negativo para saídas. */
    public BigDecimal valorComSinal() {
        return tipo.credito() ? valor : valor.negate();
    }

    public String descricao() {
        return switch (tipo) {
            case TRANSFERENCIA_ENVIADA -> "Transferência para " + contraparteNome;
            case TRANSFERENCIA_RECEBIDA -> "Transferência de " + contraparteNome;
            case PIX_ENVIADO -> "Pix para " + contraparteNome;
            case PIX_RECEBIDO -> "Pix de " + contraparteNome;
            case CAIXINHA_GUARDADO -> "Guardado em " + mensagem;
            case CAIXINHA_RESGATADO -> "Resgate de " + mensagem;
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

    public String getContraparteNome() {
        return contraparteNome;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getIdTransacao() {
        return idTransacao;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public Instant getDataHora() {
        return dataHora;
    }
}
