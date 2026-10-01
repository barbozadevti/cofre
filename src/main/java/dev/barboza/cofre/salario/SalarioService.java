package dev.barboza.cofre.salario;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.auditoria.Auditoria;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.ContaCorrente;
import dev.barboza.cofre.dominio.ContaRepository;
import dev.barboza.cofre.dominio.Dinheiro;
import dev.barboza.cofre.dominio.LancamentoRepository;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import dev.barboza.cofre.dominio.RecursoNaoEncontradoException;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.servico.Acesso;
import dev.barboza.cofre.servico.ContaService;

/**
 * "Traga seu salário": pedido de portabilidade (simulado), crédito do salário no dia do pagamento e a
 * regra "Pague-se primeiro". Para o banco, é o produto que transforma uma conta secundária em conta principal.
 */
@Service
public class SalarioService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("yyyyMM");
    private static final SecureRandom SORTEIO = new SecureRandom();

    private final PortabilidadeRepository portabilidades;
    private final ContaRepository contas;
    private final ContaService contaService;
    private final LancamentoRepository lancamentos;
    private final Auditoria auditoria;
    private final Clock relogio;

    public SalarioService(PortabilidadeRepository portabilidades, ContaRepository contas, ContaService contaService,
            LancamentoRepository lancamentos, Auditoria auditoria, Clock relogio) {
        this.portabilidades = portabilidades;
        this.contas = contas;
        this.contaService = contaService;
        this.lancamentos = lancamentos;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    public record Pedido(String bancoOrigem, String empregador, String cnpj, BigDecimal salario, Integer diaPagamento) {
    }

    public record Resumo(ContaCorrente corrente, boolean temPoupanca, List<PortabilidadeSalario> portabilidades,
            BigDecimal salariosRecebidos, long mesesRecebidos, Instant agora) {
    }

    @Transactional(readOnly = true)
    public Resumo resumo(UsuarioLogado quem) {
        ContaCorrente corrente = corrente(quem);
        return new Resumo(corrente, !contas.poupancasAtivasDoCliente(quem.clienteId()).isEmpty(),
                portabilidades.findByContaClienteIdOrderBySolicitadaEmDesc(quem.clienteId()),
                lancamentos.somar(corrente.getId(), List.of(TipoLancamento.SALARIO_PORTADO), Instant.EPOCH,
                        Instant.parse("9999-12-31T00:00:00Z")).setScale(2),
                lancamentos.countByContaIdAndTipo(corrente.getId(), TipoLancamento.SALARIO_PORTADO), relogio.instant());
    }

    @Transactional
    public PortabilidadeSalario solicitar(UsuarioLogado quem, Pedido pedido) {
        ContaCorrente corrente = corrente(quem);
        Instant agora = relogio.instant();
        boolean jaTem = portabilidades.findByContaClienteIdOrderBySolicitadaEmDesc(quem.clienteId()).stream()
                .anyMatch(p -> p.situacao(agora) != PortabilidadeSalario.Situacao.CANCELADA);
        if (jaTem) {
            throw new OperacaoInvalidaException("Você já tem uma portabilidade de salário. Cancele a atual para pedir outra.");
        }
        PortabilidadeSalario p = registrar(corrente, pedido, agora);
        auditoria.registrar(quem, "PORTABILIDADE_SOLICITADA", p.getProtocolo() + ": " + p.banco().nome() + " -> conta " + corrente.getNumero());
        return p;
    }

    /** Registra o pedido com data informada, sem checar sessão: usado também pelos dados de demonstração. */
    @Transactional
    public PortabilidadeSalario registrar(ContaCorrente corrente, Pedido pedido, Instant quando) {
        if (pedido.salario() == null || pedido.diaPagamento() == null) {
            throw new OperacaoInvalidaException("Informe o salário e o dia do pagamento.");
        }
        String protocolo = "PS" + DIA.format(quando.atZone(ContaService.FUSO)) + String.format("%06d", SORTEIO.nextInt(1_000_000));
        return portabilidades.save(new PortabilidadeSalario(protocolo, corrente, BancoDeOrigem.porCodigo(pedido.bancoOrigem()),
                pedido.empregador(), pedido.cnpj(), pedido.salario(), pedido.diaPagamento(), quando));
    }

    @Transactional
    public PortabilidadeSalario cancelar(UsuarioLogado quem, Long id) {
        PortabilidadeSalario p = portabilidades.findById(id)
                .filter(x -> x.getConta().pertenceA(quem.clienteId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido de portabilidade não encontrado."));
        p.cancelar(relogio.instant());
        auditoria.registrar(quem, "PORTABILIDADE_CANCELADA", p.getProtocolo());
        return p;
    }

    /** Liga, muda ou desliga (0%) o "Pague-se primeiro". */
    @Transactional
    public ContaCorrente definirReserva(UsuarioLogado quem, int percentual) {
        ContaCorrente corrente = corrente(quem);
        if (percentual > 0 && contas.poupancasAtivasDoCliente(quem.clienteId()).isEmpty()) {
            throw new OperacaoInvalidaException("Abra uma poupança para guardar parte do salário automaticamente.");
        }
        corrente.definirReserva(percentual);
        auditoria.registrar(quem, "PAGUE_SE_PRIMEIRO", "Conta " + corrente.getNumero() + ": " + percentual + "% do salário");
        return corrente;
    }

    /** Rotina diária: credita o salário das portabilidades concluídas que pagam neste dia. Idempotente por mês. */
    @Transactional
    public int creditarSalarios(LocalDate dia, Instant quando) {
        int creditados = 0;
        for (PortabilidadeSalario p : portabilidades.findByCanceladaEmIsNull()) {
            String id = "SAL-" + p.getId() + "-" + MES.format(dia);
            if (!p.pagaEm(dia, ContaService.FUSO) || lancamentos.existsByIdTransacao(id)) {
                continue;
            }
            ContaCorrente corrente = p.getConta();
            lancamentos.save(corrente.creditar(TipoLancamento.SALARIO_PORTADO, p.getSalario(),
                    new Conta.Contraparte(p.banco().codigo(), p.getEmpregador()),
                    "Salário · " + p.getEmpregador() + " via " + p.banco().nome(), id, quando));
            BigDecimal guardado = contaService.pagueSePrimeiro(corrente, p.getSalario(), quando);
            if (guardado.signum() > 0) {
                auditoria.registrarAvulso("sistema", "SISTEMA", "PAGUE_SE_PRIMEIRO_EXECUTADO",
                        "Conta " + corrente.getNumero() + ": " + Dinheiro.formatar(guardado));
            }
            creditados++;
        }
        return creditados;
    }

    @Transactional
    public int creditarSalarios(LocalDate dia) {
        return creditarSalarios(dia, relogio.instant());
    }

    private ContaCorrente corrente(UsuarioLogado quem) {
        Acesso.exigirCliente(quem);
        return contas.findByClienteIdOrderByNumeroBaseAsc(quem.clienteId()).stream()
                .filter(c -> c instanceof ContaCorrente)
                .map(c -> (ContaCorrente) c)
                .findFirst()
                .orElseThrow(() -> new OperacaoInvalidaException("A portabilidade de salário precisa de uma conta corrente."));
    }
}
