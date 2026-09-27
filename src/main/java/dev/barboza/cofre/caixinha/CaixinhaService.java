package dev.barboza.cofre.caixinha;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.auditoria.Auditoria;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.Dinheiro;
import dev.barboza.cofre.dominio.IdTransacao;
import dev.barboza.cofre.dominio.LancamentoRepository;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import dev.barboza.cofre.dominio.RecursoNaoEncontradoException;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.servico.Acesso;
import dev.barboza.cofre.servico.ContaService;

@Service
public class CaixinhaService {

    public static final int MAXIMO_DE_CAIXINHAS = 10;

    private final CaixinhaRepository caixinhas;
    private final ContaService contas;
    private final LancamentoRepository lancamentos;
    private final Auditoria auditoria;
    private final Clock relogio;

    public CaixinhaService(CaixinhaRepository caixinhas, ContaService contas, LancamentoRepository lancamentos,
            Auditoria auditoria, Clock relogio) {
        this.caixinhas = caixinhas;
        this.contas = contas;
        this.lancamentos = lancamentos;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<Caixinha> listar(UsuarioLogado quem, String numeroConta) {
        Conta conta = contas.buscar(quem, numeroConta);
        return caixinhas.findByContaIdOrderByIdAsc(conta.getId());
    }

    @Transactional
    public Caixinha criar(UsuarioLogado quem, String numeroConta, String nome, BigDecimal meta) {
        Conta conta = contas.buscarInterno(numeroConta);
        Acesso.exigirDono(quem, conta);
        return criarEm(conta, nome, meta, relogio.instant());
    }

    @Transactional
    public Caixinha criarEm(Conta conta, String nome, BigDecimal meta, Instant quando) {
        conta.exigirAtiva();
        if (caixinhas.countByContaId(conta.getId()) >= MAXIMO_DE_CAIXINHAS) {
            throw new OperacaoInvalidaException("Cada conta pode ter no máximo " + MAXIMO_DE_CAIXINHAS + " caixinhas.");
        }
        return caixinhas.save(new Caixinha(conta, nome, meta, quando));
    }

    /** Guarda só dinheiro próprio: o cheque especial não pode ir para a caixinha. */
    @Transactional
    public Caixinha guardar(UsuarioLogado quem, Long id, BigDecimal valor) {
        Caixinha caixinha = daPessoa(quem, id);
        guardarEm(caixinha, valor, relogio.instant());
        auditoria.registrar(quem, "CAIXINHA_GUARDAR", caixinha.getNome() + ": " + Dinheiro.formatar(valor));
        return caixinha;
    }

    @Transactional
    public void guardarEm(Long id, BigDecimal valor, Instant quando) {
        guardarEm(caixinhas.findById(id).orElseThrow(), valor, quando);
    }

    private void guardarEm(Caixinha caixinha, BigDecimal valor, Instant quando) {
        Conta conta = caixinha.getConta();
        lancamentos.save(conta.debitar(TipoLancamento.CAIXINHA_GUARDADO, valor, null, caixinha.getNome(),
                IdTransacao.gerar('T', quando), quando, false));
        caixinha.somar(Dinheiro.validarValor(valor));
    }

    @Transactional
    public Caixinha resgatar(UsuarioLogado quem, Long id, BigDecimal valor) {
        Caixinha caixinha = daPessoa(quem, id);
        BigDecimal valido = Dinheiro.validarValor(valor);
        caixinha.retirar(valido);
        Instant agora = relogio.instant();
        lancamentos.save(caixinha.getConta().creditar(TipoLancamento.CAIXINHA_RESGATADO, valido, null, caixinha.getNome(),
                IdTransacao.gerar('T', agora), agora));
        auditoria.registrar(quem, "CAIXINHA_RESGATAR", caixinha.getNome() + ": " + Dinheiro.formatar(valido));
        return caixinha;
    }

    @Transactional
    public void excluir(UsuarioLogado quem, Long id) {
        Caixinha caixinha = daPessoa(quem, id);
        if (caixinha.getSaldo().signum() != 0) {
            throw new OperacaoInvalidaException("Resgate os " + Dinheiro.formatar(caixinha.getSaldo())
                    + " antes de excluir a caixinha.");
        }
        caixinhas.delete(caixinha);
    }

    private Caixinha daPessoa(UsuarioLogado quem, Long id) {
        Caixinha caixinha = caixinhas.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Caixinha não encontrada."));
        if (!caixinha.getConta().pertenceA(quem.clienteId())) {
            throw new RecursoNaoEncontradoException("Caixinha não encontrada.");
        }
        return caixinha;
    }
}
