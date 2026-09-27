package dev.barboza.cofre.servico;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.auditoria.Auditoria;
import dev.barboza.cofre.auditoria.AuditoriaRepository;
import dev.barboza.cofre.auditoria.EventoAuditoria;
import dev.barboza.cofre.caixinha.CaixinhaRepository;
import dev.barboza.cofre.cartao.CartaoRepository;
import dev.barboza.cofre.dominio.Cliente;
import dev.barboza.cofre.dominio.ClienteRepository;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.ContaRepository;
import dev.barboza.cofre.dominio.Cpf;
import dev.barboza.cofre.dominio.Dinheiro;
import dev.barboza.cofre.dominio.LancamentoRepository;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import dev.barboza.cofre.dominio.RecursoNaoEncontradoException;
import dev.barboza.cofre.dominio.SituacaoConta;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.pix.PixService;
import dev.barboza.cofre.seguranca.Perfil;
import dev.barboza.cofre.seguranca.Usuario;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.seguranca.UsuarioRepository;

/** Backoffice do gerente: abertura de contas, limites, bloqueios, encerramento, indicadores e auditoria. */
@Service
public class GerenciaService {

    private static final String ALFABETO_SENHA = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom SORTEIO = new SecureRandom();

    private final ContaService contaService;
    private final PixService pixService;
    private final ClienteRepository clientes;
    private final ContaRepository contas;
    private final UsuarioRepository usuarios;
    private final CaixinhaRepository caixinhas;
    private final CartaoRepository cartoes;
    private final LancamentoRepository lancamentos;
    private final AuditoriaRepository eventos;
    private final Auditoria auditoria;
    private final PasswordEncoder senhas;
    private final Clock relogio;

    public GerenciaService(ContaService contaService, PixService pixService, ClienteRepository clientes,
            ContaRepository contas, UsuarioRepository usuarios, CaixinhaRepository caixinhas, CartaoRepository cartoes,
            LancamentoRepository lancamentos, AuditoriaRepository eventos, Auditoria auditoria, PasswordEncoder senhas,
            Clock relogio) {
        this.contaService = contaService;
        this.pixService = pixService;
        this.clientes = clientes;
        this.contas = contas;
        this.usuarios = usuarios;
        this.caixinhas = caixinhas;
        this.cartoes = cartoes;
        this.lancamentos = lancamentos;
        this.eventos = eventos;
        this.auditoria = auditoria;
        this.senhas = senhas;
        this.relogio = relogio;
    }

    public record NovaConta(String nome, String cpf, String email, String telefone, String agencia,
            BigDecimal depositoInicial, BigDecimal limite) {
    }

    /** {@code senhaProvisoria} só vem preenchida quando o cliente é novo; ela não é guardada em texto. */
    public record ContaAberta(Conta conta, String senhaProvisoria, String mensagem) {
    }

    public record Indicadores(long clientes, long contasAtivas, long contasBloqueadas, BigDecimal emCustodia,
            BigDecimal emCaixinhas, BigDecimal limiteConcedido, BigDecimal limiteEmUso, long pixHoje,
            BigDecimal volumePixHoje) {
    }

    /** Abre conta. Se o CPF já é cliente, abre uma conta adicional; senão cadastra cliente e acesso ao app. */
    @Transactional
    public ContaAberta abrirConta(UsuarioLogado quem, NovaConta dados) {
        Acesso.exigirGerente(quem);
        Instant agora = relogio.instant();
        String cpf = Cpf.validar(dados.cpf());
        Cliente cliente = clientes.findByCpf(cpf).orElse(null);
        String senhaProvisoria = null;
        if (cliente == null) {
            cliente = new Cliente(dados.nome(), cpf, dados.email(), dados.telefone(), agora);
            if (clientes.existsByEmail(cliente.getEmail())) {
                throw new OperacaoInvalidaException("Já existe um cliente com esse e-mail.");
            }
            cliente = clientes.save(cliente);
            senhaProvisoria = gerarSenhaProvisoria();
            usuarios.save(new Usuario(cliente.getEmail(), cliente.getNome(), senhas.encode(senhaProvisoria),
                    Perfil.CLIENTE, cliente, true, agora));
        }
        Conta conta = contaService.abrirPara(cliente, dados.agencia(),
                dados.depositoInicial() == null ? BigDecimal.ZERO : dados.depositoInicial(), agora);
        if (dados.limite() != null && dados.limite().signum() > 0) {
            conta.definirLimite(dados.limite());
        }
        auditoria.registrar(quem, "CONTA_ABERTA", "Conta " + conta.getNumero() + " para " + cliente.getNome()
                + (senhaProvisoria != null ? " (cliente novo)" : " (conta adicional)"));
        return new ContaAberta(conta, senhaProvisoria, conta.mensagemDeBoasVindas());
    }

    @Transactional
    public Conta definirLimite(UsuarioLogado quem, String numero, BigDecimal limite) {
        Acesso.exigirGerente(quem);
        Conta conta = contaService.buscarInterno(numero);
        BigDecimal anterior = conta.getLimite();
        conta.definirLimite(limite);
        auditoria.registrar(quem, "LIMITE_ALTERADO", "Conta " + conta.getNumero() + ": " + Dinheiro.formatar(anterior)
                + " -> " + Dinheiro.formatar(conta.getLimite()));
        return conta;
    }

    @Transactional
    public Conta bloquear(UsuarioLogado quem, String numero, String motivo) {
        Acesso.exigirGerente(quem);
        Conta conta = contaService.buscarInterno(numero);
        conta.bloquear(motivo);
        auditoria.registrar(quem, "CONTA_BLOQUEADA", "Conta " + conta.getNumero() + ": " + conta.getMotivoBloqueio());
        return conta;
    }

    @Transactional
    public Conta desbloquear(UsuarioLogado quem, String numero) {
        Acesso.exigirGerente(quem);
        Conta conta = contaService.buscarInterno(numero);
        conta.desbloquear();
        auditoria.registrar(quem, "CONTA_DESBLOQUEADA", "Conta " + conta.getNumero());
        return conta;
    }

    /** Encerra: exige saldo e caixinhas zerados; remove as chaves Pix e bloqueia o cartão. */
    @Transactional
    public Conta encerrar(UsuarioLogado quem, String numero) {
        Acesso.exigirGerente(quem);
        Conta conta = contaService.buscarInterno(numero);
        BigDecimal emCaixinhas = caixinhas.totalDaConta(conta.getId());
        if (emCaixinhas.signum() != 0) {
            throw new OperacaoInvalidaException("O cliente ainda tem " + Dinheiro.formatar(emCaixinhas)
                    + " em caixinhas; é preciso resgatar antes de encerrar.");
        }
        conta.encerrar(relogio.instant());
        pixService.removerChavesDa(conta);
        caixinhas.deleteAll(caixinhas.findByContaIdOrderByIdAsc(conta.getId()));
        cartoes.findByContaId(conta.getId()).ifPresent(c -> c.definirBloqueio(true));
        auditoria.registrar(quem, "CONTA_ENCERRADA", "Conta " + conta.getNumero());
        return conta;
    }

    /** Gera nova senha provisória para o cliente (ex.: esqueceu a senha) e desbloqueia o acesso. */
    @Transactional
    public String redefinirSenha(UsuarioLogado quem, String numeroConta) {
        Acesso.exigirGerente(quem);
        Conta conta = contaService.buscarInterno(numeroConta);
        Usuario usuario = usuarios.findByClienteId(conta.getCliente().getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente sem acesso ao app."));
        String senha = gerarSenhaProvisoria();
        usuario.trocarSenha(senhas.encode(senha), true);
        auditoria.registrar(quem, "SENHA_REDEFINIDA", "Cliente " + usuario.getNome());
        return senha;
    }

    @Transactional(readOnly = true)
    public Indicadores indicadores(UsuarioLogado quem) {
        Acesso.exigirGerente(quem);
        LocalDate hoje = LocalDate.now(relogio.withZone(ContaService.FUSO));
        List<Object[]> pix = lancamentos.contarESomar(TipoLancamento.PIX_ENVIADO,
                hoje.atStartOfDay(ContaService.FUSO).toInstant(), hoje.plusDays(1).atStartOfDay(ContaService.FUSO).toInstant());
        Object[] linha = pix.isEmpty() ? new Object[] {0L, BigDecimal.ZERO} : pix.getFirst();
        return new Indicadores(clientes.count(), contas.countBySituacao(SituacaoConta.ATIVA),
                contas.countBySituacao(SituacaoConta.BLOQUEADA), contas.totalEmCustodia().setScale(2),
                caixinhas.totalGeral().setScale(2), contas.totalDeLimiteConcedido().setScale(2),
                contas.totalDeLimiteEmUso().setScale(2), ((Number) linha[0]).longValue(),
                new BigDecimal(linha[1].toString()).setScale(2));
    }

    @Transactional(readOnly = true)
    public Page<EventoAuditoria> auditoria(UsuarioLogado quem, int pagina) {
        Acesso.exigirGerente(quem);
        return eventos.findAllByOrderByDataHoraDescIdDesc(PageRequest.of(Math.max(0, pagina), 30));
    }

    /** 10 caracteres sem letras ambíguas (l, I, O, 0, 1), sempre com letra e número. */
    static String gerarSenhaProvisoria() {
        while (true) {
            StringBuilder senha = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                senha.append(ALFABETO_SENHA.charAt(SORTEIO.nextInt(ALFABETO_SENHA.length())));
            }
            String s = senha.toString();
            if (s.matches(".*\\d.*") && s.matches(".*[a-zA-Z].*")) {
                return s;
            }
        }
    }
}
