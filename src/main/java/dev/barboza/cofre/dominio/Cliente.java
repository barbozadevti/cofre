package dev.barboza.cofre.dominio;

import java.time.Instant;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cliente")
public class Cliente {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(nullable = false, length = 11, unique = true)
    private String cpf;

    @Column(nullable = false, length = 120, unique = true)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Cliente() {
    }

    public Cliente(String nome, String cpf, String email, String telefone, Instant criadoEm) {
        this.nome = validarNome(nome);
        this.cpf = Cpf.validar(cpf);
        this.email = validarEmail(email);
        this.telefone = telefone == null || telefone.isBlank() ? null : Telefone.normalizar(telefone);
        this.criadoEm = criadoEm;
    }

    public static String validarNome(String nome) {
        String limpo = nome == null ? "" : nome.trim().replaceAll("\\s+", " ");
        if (limpo.length() < 3) {
            throw new OperacaoInvalidaException("Informe o nome do cliente (pelo menos 3 letras).");
        }
        if (limpo.length() > 80) {
            throw new OperacaoInvalidaException("O nome pode ter no máximo 80 caracteres.");
        }
        if (!limpo.matches("[\\p{L} .'-]+")) {
            throw new OperacaoInvalidaException("O nome deve ter apenas letras.");
        }
        return limpo;
    }

    public static String validarEmail(String email) {
        String limpo = email == null ? "" : email.trim().toLowerCase();
        if (limpo.length() > 120 || !EMAIL.matcher(limpo).matches()) {
            throw new OperacaoInvalidaException("E-mail inválido.");
        }
        return limpo;
    }

    /** Primeiro nome, para saudações ("Olá, Mario"). */
    public String primeiroNome() {
        return nome.split(" ")[0];
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getCpf() {
        return cpf;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
