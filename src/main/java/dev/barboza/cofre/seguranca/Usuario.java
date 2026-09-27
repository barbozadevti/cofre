package dev.barboza.cofre.seguranca;

import java.time.Duration;
import java.time.Instant;

import dev.barboza.cofre.dominio.Cliente;
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
@Table(name = "usuario")
public class Usuario {

    public static final int TENTATIVAS_ANTES_DO_BLOQUEIO = 5;
    public static final Duration TEMPO_DE_BLOQUEIO = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** E-mail (clientes também entram pelo CPF). */
    @Column(nullable = false, unique = true, length = 120)
    private String login;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Perfil perfil;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "tentativas_falhas", nullable = false)
    private int tentativasFalhas;

    @Column(name = "bloqueado_ate")
    private Instant bloqueadoAte;

    /** Senha provisória (criada pelo gerente): o usuário precisa trocar no primeiro acesso. */
    @Column(name = "trocar_senha", nullable = false)
    private boolean trocarSenha;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Usuario() {
    }

    public Usuario(String login, String nome, String senhaHash, Perfil perfil, Cliente cliente, boolean trocarSenha,
            Instant criadoEm) {
        this.login = login.trim().toLowerCase();
        this.nome = nome;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
        this.cliente = cliente;
        this.trocarSenha = trocarSenha;
        this.ativo = true;
        this.criadoEm = criadoEm;
    }

    public boolean bloqueadoEm(Instant agora) {
        return bloqueadoAte != null && bloqueadoAte.isAfter(agora);
    }

    /** Conta uma senha errada; na quinta seguida, bloqueia o acesso por 15 minutos. Devolve true se bloqueou. */
    public boolean registrarFalha(Instant agora) {
        tentativasFalhas++;
        if (tentativasFalhas >= TENTATIVAS_ANTES_DO_BLOQUEIO) {
            bloqueadoAte = agora.plus(TEMPO_DE_BLOQUEIO);
            tentativasFalhas = 0;
            return true;
        }
        return false;
    }

    public void registrarSucesso() {
        tentativasFalhas = 0;
        bloqueadoAte = null;
    }

    public void trocarSenha(String novoHash, boolean provisoria) {
        this.senhaHash = novoHash;
        this.trocarSenha = provisoria;
        registrarSucesso();
    }

    public UsuarioLogado comoLogado() {
        return new UsuarioLogado(id, login, nome, perfil, cliente == null ? null : cliente.getId());
    }

    public Long getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getNome() {
        return nome;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public int getTentativasFalhas() {
        return tentativasFalhas;
    }

    public Instant getBloqueadoAte() {
        return bloqueadoAte;
    }

    public boolean isTrocarSenha() {
        return trocarSenha;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
