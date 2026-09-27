package dev.barboza.cofre.auditoria;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Registro de quem fez o quê e quando. Só é gravado, nunca alterado. */
@Entity
@Table(name = "evento_auditoria")
public class EventoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;

    @Column(nullable = false, length = 120)
    private String usuario;

    @Column(length = 10)
    private String perfil;

    @Column(nullable = false, length = 40)
    private String acao;

    @Column(length = 300)
    private String detalhe;

    @Column(length = 45)
    private String origem;

    protected EventoAuditoria() {
    }

    public EventoAuditoria(Instant dataHora, String usuario, String perfil, String acao, String detalhe, String origem) {
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
        this.acao = acao;
        this.detalhe = detalhe != null && detalhe.length() > 300 ? detalhe.substring(0, 300) : detalhe;
        this.origem = origem;
    }

    public Long getId() {
        return id;
    }

    public Instant getDataHora() {
        return dataHora;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getPerfil() {
        return perfil;
    }

    public String getAcao() {
        return acao;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public String getOrigem() {
        return origem;
    }
}
