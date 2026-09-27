package dev.barboza.cofre.servico;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.auditoria.Auditoria;
import dev.barboza.cofre.dominio.Cliente;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.ContaNaoEncontradaException;
import dev.barboza.cofre.dominio.ContaRepository;
import dev.barboza.cofre.dominio.Cpf;
import dev.barboza.cofre.dominio.Dinheiro;
import dev.barboza.cofre.dominio.IdTransacao;
import dev.barboza.cofre.dominio.Lancamento;
import dev.barboza.cofre.dominio.LancamentoRepository;
import dev.barboza.cofre.dominio.NumeroConta;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import dev.barboza.cofre.dominio.RecursoNaoEncontradoException;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.seguranca.UsuarioLogado;

/** Casos de uso de conta corrente. Site, API e terminal passam todos por aqui. */
@Service
public class ContaService {

    /** Fuso usado para "dia" no extrato, nos limites do Pix e nos juros. */
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    /** Primeiro número de conta emitido. */
    static final int PRIMEIRO_NUMERO = 10001;

    private final ContaRepository contas;
    private final LancamentoRepository lancamentos;
    private final Auditoria auditoria;
    private final Clock relogio;

    public ContaService(ContaRepository contas, LancamentoRepository lancamentos, Auditoria auditoria, Clock relogio) {
        this.contas = contas;
        this.lancamentos = lancamentos;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    // ---------- Consulta ----------

    @Transactional(readOnly = true)
    public List<Conta> contasVisiveis(UsuarioLogado quem) {
        return quem.clienteId() != null
                ? contas.findByClienteIdOrderByNumeroBaseAsc(quem.clienteId())
                : contas.findAllByOrderByNumeroBaseAsc();
    }

    @Transactional(readOnly = true)
    public Conta buscar(UsuarioLogado quem, String numero) {
        Conta conta = buscarInterno(numero);
        Acesso.exigirVisao(quem, conta);
        return conta;
    }

    /** Busca sem checar acesso: só para uso entre serviços. */
    @Transactional(readOnly = true)
    public Conta buscarInterno(String numero) {
        String normalizado = NumeroConta.normalizar(numero);
        return contas.findByNumero(normalizado).orElseThrow(() -> new ContaNaoEncontradaException(normalizado));
    }

    /** Busca por nome, CPF ou número de conta (balcão e gerência). */
    @Transactional(readOnly = true)
    public List<Conta> pesquisar(UsuarioLogado quem, String termo) {
        Acesso.exigirFuncionario(quem);
        String texto = termo == null ? "" : termo.trim();
        if (texto.isEmpty()) {
            return contas.findAllByOrderByNumeroBaseAsc();
        }
        String digitos = texto.replaceAll("\\D", "");
        return contas.buscar(texto, digitos.isEmpty() ? "#" : digitos);
    }

    // ---------- Abertura (usada pela gerência e pelos dados de demonstração) ----------

    @Transactional
    public Conta abrirPara(Cliente cliente, String agencia, BigDecimal depositoInicial, Instant quando) {
        int proximo = Math.max(contas.maiorNumeroBase() + 1, PRIMEIRO_NUMERO);
        Conta.Abertura abertura = Conta.abrir(proximo, agencia, cliente, depositoInicial, quando);
        Conta conta = contas.save(abertura.conta());
        lancamentos.save(abertura.lancamento());
        return conta;
    }

    // ---------- Balcão (caixa e gerente) ----------

    @Transactional
    public Conta depositarEmEspecie(UsuarioLogado quem, String numero, BigDecimal valor) {
        Acesso.exigirFuncionario(quem);
        Conta conta = depositarEm(numero, valor, "Depósito no caixa", relogio.instant());
        auditoria.registrar(quem, "DEPOSITO_ESPECIE", "Conta " + conta.getNumero() + ": " + Dinheiro.formatar(valor));
        return conta;
    }

    @Transactional
    public Conta sacarEmEspecie(UsuarioLogado quem, String numero, BigDecimal valor) {
        Acesso.exigirFuncionario(quem);
        Conta conta = sacarEm(numero, valor, "Saque no caixa", relogio.instant());
        auditoria.registrar(quem, "SAQUE_ESPECIE", "Conta " + conta.getNumero() + ": " + Dinheiro.formatar(valor));
        return conta;
    }

    @Transactional
    public Conta depositarEm(String numero, BigDecimal valor, String mensagem, Instant quando) {
        Conta conta = buscarInterno(numero);
        lancamentos.save(conta.creditar(TipoLancamento.DEPOSITO, valor, null, mensagem, IdTransacao.gerar('T', quando), quando));
        return conta;
    }

    @Transactional
    public Conta sacarEm(String numero, BigDecimal valor, String mensagem, Instant quando) {
        Conta conta = buscarInterno(numero);
        lancamentos.save(conta.debitar(TipoLancamento.SAQUE, valor, null, mensagem, IdTransacao.gerar('T', quando), quando, true));
        return conta;
    }

    // ---------- Transferências (cliente) ----------

    /** Transferência entre contas do Cofre, feita pelo dono da conta de origem. */
    @Transactional
    public Comprovante transferir(UsuarioLogado quem, String origem, String destino, BigDecimal valor, String mensagem,
            String chaveIdempotencia) {
        Conta contaOrigem = buscarInterno(origem);
        Acesso.exigirDono(quem, contaOrigem);
        Optional<Comprovante> repetida = repetida(contaOrigem, chaveIdempotencia, quem);
        if (repetida.isPresent()) {
            return repetida.get();
        }
        Conta contaDestino = buscarInterno(destino);
        String id = movimentar(contaOrigem, contaDestino, valor, mensagem, TipoLancamento.TRANSFERENCIA_ENVIADA,
                TipoLancamento.TRANSFERENCIA_RECEBIDA, 'T', relogio.instant(), chaveIdempotencia);
        auditoria.registrar(quem, "TRANSFERENCIA", contaOrigem.getNumero() + " -> " + contaDestino.getNumero() + ": "
                + Dinheiro.formatar(valor));
        return comprovanteInterno(id);
    }

    /**
     * Move dinheiro entre duas contas numa única transação: ou as duas mudam, ou nenhuma muda.
     * Devolve o id da transação, compartilhado pelos dois lançamentos.
     */
    @Transactional
    public String movimentar(Conta origem, Conta destino, BigDecimal valor, String mensagem, TipoLancamento saida,
            TipoLancamento entrada, char prefixo, Instant quando, String chaveIdempotencia) {
        if (origem.getNumero().equals(destino.getNumero())) {
            throw new OperacaoInvalidaException("A conta de destino deve ser diferente da conta de origem.");
        }
        String id = IdTransacao.gerar(prefixo, quando);
        destino.exigirAtiva();
        Lancamento enviado = origem.debitar(saida, valor, Conta.Contraparte.de(destino), mensagem, id, quando, true);
        enviado.marcarIdempotencia(chaveIdempotencia);
        Lancamento recebido = destino.creditar(entrada, valor, Conta.Contraparte.de(origem), mensagem, id, quando);
        lancamentos.saveAll(List.of(enviado, recebido));
        return id;
    }

    /** Se o cliente reenviar a mesma operação (mesma Idempotency-Key), devolve o comprovante da primeira. */
    @Transactional(readOnly = true)
    public Optional<Comprovante> repetida(Conta origem, String chaveIdempotencia, UsuarioLogado quem) {
        if (chaveIdempotencia == null || chaveIdempotencia.isBlank()) {
            return Optional.empty();
        }
        if (chaveIdempotencia.length() > 64) {
            throw new OperacaoInvalidaException("Idempotency-Key deve ter no máximo 64 caracteres.");
        }
        return lancamentos.findByContaIdAndChaveIdempotencia(origem.getId(), chaveIdempotencia)
                .map(l -> comprovanteInterno(l.getIdTransacao()));
    }

    // ---------- Extrato e comprovantes ----------

    /** Extrato de {@code de} até {@code ate} (inclusive). Sem datas, mostra os últimos 30 dias. */
    @Transactional(readOnly = true)
    public Extrato extrato(UsuarioLogado quem, String numero, LocalDate de, LocalDate ate) {
        Conta conta = buscar(quem, numero);
        LocalDate hoje = LocalDate.now(relogio.withZone(FUSO));
        LocalDate fim = ate != null ? ate : hoje;
        LocalDate inicio = de != null ? de : fim.minusDays(29);
        if (inicio.isAfter(fim)) {
            throw new OperacaoInvalidaException("A data inicial não pode ser depois da data final.");
        }
        if (inicio.isBefore(fim.minusYears(1))) {
            throw new OperacaoInvalidaException("O período do extrato pode ter no máximo 1 ano.");
        }
        Instant desde = inicio.atStartOfDay(FUSO).toInstant();
        Instant ateExclusivo = fim.plusDays(1).atStartOfDay(FUSO).toInstant();

        BigDecimal saldoInicial = lancamentos
                .findFirstByContaIdAndDataHoraLessThanOrderByDataHoraDescIdDesc(conta.getId(), desde)
                .map(Lancamento::getSaldoApos)
                .orElse(BigDecimal.ZERO.setScale(2));
        List<Lancamento> doPeriodo = lancamentos
                .findByContaIdAndDataHoraGreaterThanEqualAndDataHoraLessThanOrderByDataHoraAscIdAsc(
                        conta.getId(), desde, ateExclusivo);
        return new Extrato(conta, inicio, fim, saldoInicial, doPeriodo);
    }

    @Transactional(readOnly = true)
    public Comprovante comprovante(UsuarioLogado quem, String idTransacao) {
        List<Lancamento> partes = lancamentos.findByIdTransacaoOrderByIdAsc(idTransacao);
        boolean podeVer = !partes.isEmpty() && (quem.perfil().funcionario()
                || partes.stream().anyMatch(l -> l.getConta().pertenceA(quem.clienteId())));
        if (!podeVer) {
            throw new RecursoNaoEncontradoException("Comprovante não encontrado.");
        }
        return montar(partes);
    }

    @Transactional(readOnly = true)
    public Comprovante comprovanteInterno(String idTransacao) {
        return montar(lancamentos.findByIdTransacaoOrderByIdAsc(idTransacao));
    }

    private static Comprovante montar(List<Lancamento> partes) {
        Lancamento debito = partes.stream().filter(l -> !l.getTipo().credito()).findFirst().orElse(null);
        Lancamento credito = partes.stream().filter(l -> l.getTipo().credito()).findFirst().orElse(null);
        Lancamento principal = debito != null ? debito : credito;
        String tipo = switch (principal.getTipo()) {
            case PIX_ENVIADO, PIX_RECEBIDO -> "PIX";
            case TRANSFERENCIA_ENVIADA, TRANSFERENCIA_RECEBIDA -> "TRANSFERENCIA";
            default -> principal.getTipo().name();
        };
        String titulo = switch (tipo) {
            case "PIX" -> "Comprovante de Pix";
            case "TRANSFERENCIA" -> "Comprovante de transferência";
            default -> principal.getTipo().descricao();
        };
        return new Comprovante(principal.getIdTransacao(), tipo, titulo, principal.getDataHora(), principal.getValor(),
                debito == null ? null : parte(debito.getConta()),
                credito == null ? null : parte(credito.getConta()),
                principal.getTipo().name().startsWith("CAIXINHA") ? null : principal.getMensagem());
    }

    private static Comprovante.Parte parte(Conta conta) {
        return new Comprovante.Parte(conta.getCliente().getNome(), Cpf.mascarar(conta.getCliente().getCpf()),
                conta.getAgencia(), conta.getNumero());
    }

    @Transactional(readOnly = true)
    public List<Lancamento> ultimos(UsuarioLogado quem, String numero) {
        Conta conta = buscar(quem, numero);
        return lancamentos.findTop8ByContaIdOrderByDataHoraDescIdDesc(conta.getId());
    }
}
