package dev.barboza.cofre;

import java.math.BigDecimal;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

import dev.barboza.cofre.dominio.Cliente;
import dev.barboza.cofre.dominio.ClienteRepository;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.Cpf;
import dev.barboza.cofre.pix.PixService;
import dev.barboza.cofre.pix.TipoChavePix;
import dev.barboza.cofre.seguranca.Perfil;
import dev.barboza.cofre.seguranca.Usuario;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.seguranca.UsuarioRepository;
import dev.barboza.cofre.servico.ContaService;

/** Monta clientes, contas e funcionários para os testes (senha de todos: {@value #SENHA}). */
@TestComponent
public class Cenario {

    public static final String SENHA = "Teste1234";

    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final ContaService contas;
    private final PixService pix;
    private final PasswordEncoder senhas;
    private String hash;
    private int sequencia = 100000000;

    public Cenario(ClienteRepository clientes, UsuarioRepository usuarios, ContaService contas, PixService pix,
            PasswordEncoder senhas) {
        this.clientes = clientes;
        this.usuarios = usuarios;
        this.contas = contas;
        this.pix = pix;
        this.senhas = senhas;
    }

    public record Pessoa(Cliente cliente, UsuarioLogado usuario, Conta conta) {
    }

    /** Cliente com acesso ao app e uma conta com saldo e limite (strings: "100.00"). */
    public Pessoa cliente(String nome, String saldo, String limite) {
        sequencia += 7919;
        String cpf = Cpf.completar(String.valueOf(sequencia));
        String email = nome.toLowerCase().replace(" ", ".").replaceAll("[^a-z.]", "") + "@teste.dev";
        Cliente cliente = clientes.save(new Cliente(nome, cpf, email, null, RelogioFixo.AGORA));
        Usuario usuario = usuarios.save(new Usuario(email, nome, hash(), Perfil.CLIENTE, cliente, false, RelogioFixo.AGORA));
        Conta conta = contas.abrirPara(cliente, null, new BigDecimal(saldo), RelogioFixo.AGORA);
        conta.definirLimite(new BigDecimal(limite));
        return new Pessoa(cliente, usuario.comoLogado(), conta);
    }

    public UsuarioLogado funcionario(Perfil perfil) {
        String login = perfil.name().toLowerCase() + sequencia++ + "@cofre.dev";
        return usuarios.save(new Usuario(login, perfil.nome() + " de Teste", hash(), perfil, null, false, RelogioFixo.AGORA))
                .comoLogado();
    }

    public void chave(Pessoa pessoa, TipoChavePix tipo, String valor) {
        pix.cadastrarEm(pessoa.conta(), tipo, valor, RelogioFixo.AGORA, UsuarioLogado.SISTEMA);
    }

    private String hash() {
        if (hash == null) {
            hash = senhas.encode(SENHA);
        }
        return hash;
    }
}
