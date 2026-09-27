package dev.barboza.cofre.pix;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.auditoria.Auditoria;
import dev.barboza.cofre.dominio.Cliente;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.Cpf;
import dev.barboza.cofre.dominio.Dinheiro;
import dev.barboza.cofre.dominio.LancamentoRepository;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import dev.barboza.cofre.dominio.RecursoNaoEncontradoException;
import dev.barboza.cofre.dominio.Telefone;
import dev.barboza.cofre.dominio.TipoLancamento;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.servico.Acesso;
import dev.barboza.cofre.servico.Comprovante;
import dev.barboza.cofre.servico.ContaService;

/**
 * Pix entre contas do Cofre: chaves, envio, cobrança (QR Code / copia e cola) e limites.
 * As regras de horário seguem o Banco Central: das 20h às 6h vale o limite noturno.
 */
@Service
public class PixService {

    public static final BigDecimal LIMITE_NOTURNO = new BigDecimal("1000.00");
    public static final BigDecimal LIMITE_DIURNO = new BigDecimal("20000.00");
    public static final int MAXIMO_DE_CHAVES = 5;
    public static final String CIDADE = "SAO PAULO";

    private final ChavePixRepository chaves;
    private final ContaService contas;
    private final LancamentoRepository lancamentos;
    private final Auditoria auditoria;
    private final Clock relogio;

    public PixService(ChavePixRepository chaves, ContaService contas, LancamentoRepository lancamentos,
            Auditoria auditoria, Clock relogio) {
        this.chaves = chaves;
        this.contas = contas;
        this.lancamentos = lancamentos;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    public record Destinatario(String chave, TipoChavePix tipoChave, String nome, String cpfMascarado,
            String instituicao, String agencia, String conta) {
    }

    public record Cobranca(String copiaECola, String qrCodeSvg, BigDecimal valor, String chave) {
    }

    public record LeituraCopiaECola(Destinatario destinatario, BigDecimal valor, String descricao, String txid) {
    }

    public record Limites(BigDecimal limitePeriodo, BigDecimal usadoNoPeriodo, BigDecimal disponivelNoPeriodo,
            boolean noturno, Instant fimDoPeriodo) {
    }

    // ---------- Chaves ----------

    @Transactional(readOnly = true)
    public List<ChavePix> chaves(UsuarioLogado quem, String numeroConta) {
        Conta conta = contas.buscar(quem, numeroConta);
        return chaves.findByContaIdOrderByIdAsc(conta.getId());
    }

    @Transactional
    public ChavePix cadastrar(UsuarioLogado quem, String numeroConta, TipoChavePix tipo, String valor) {
        Conta conta = contas.buscarInterno(numeroConta);
        Acesso.exigirDono(quem, conta);
        return cadastrarEm(conta, tipo, valor, relogio.instant(), quem);
    }

    @Transactional
    public ChavePix cadastrarEm(Conta conta, TipoChavePix tipo, String valor, Instant quando, UsuarioLogado quem) {
        conta.exigirAtiva();
        if (tipo == null) {
            throw new OperacaoInvalidaException("Escolha o tipo da chave.");
        }
        if (chaves.countByContaId(conta.getId()) >= MAXIMO_DE_CHAVES) {
            throw new OperacaoInvalidaException("Cada conta pode ter no máximo " + MAXIMO_DE_CHAVES + " chaves Pix.");
        }
        Cliente titular = conta.getCliente();
        String normalizada = switch (tipo) {
            case CPF -> {
                String cpf = Cpf.validar(valor == null || valor.isBlank() ? titular.getCpf() : valor);
                if (!cpf.equals(titular.getCpf())) {
                    throw new OperacaoInvalidaException("A chave CPF precisa ser o CPF do titular da conta.");
                }
                yield cpf;
            }
            case EMAIL -> Cliente.validarEmail(valor);
            case TELEFONE -> Telefone.normalizar(valor);
            case ALEATORIA -> UUID.randomUUID().toString();
        };
        if (chaves.existsByValor(normalizada)) {
            throw new OperacaoInvalidaException("Essa chave já está cadastrada no Cofre.");
        }
        ChavePix chave = chaves.save(new ChavePix(conta, tipo, normalizada, quando));
        auditoria.registrar(quem, "PIX_CHAVE_CRIADA", tipo.nome() + " na conta " + conta.getNumero());
        return chave;
    }

    @Transactional
    public void excluir(UsuarioLogado quem, Long idChave) {
        ChavePix chave = chaves.findById(idChave)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chave Pix não encontrada."));
        if (!chave.getConta().pertenceA(quem.clienteId())) {
            throw new RecursoNaoEncontradoException("Chave Pix não encontrada.");
        }
        chaves.delete(chave);
        auditoria.registrar(quem, "PIX_CHAVE_EXCLUIDA", chave.getTipo().nome() + " da conta " + chave.getConta().getNumero());
    }

    /** Antes de enviar, o app mostra para quem vai o dinheiro (nome e CPF mascarado), como nos bancos. */
    @Transactional(readOnly = true)
    public Destinatario consultar(String chaveDigitada) {
        ChavePix chave = localizar(chaveDigitada);
        Conta conta = chave.getConta();
        return new Destinatario(chave.getValor(), chave.getTipo(), conta.getCliente().getNome(),
                Cpf.mascarar(conta.getCliente().getCpf()), "Cofre (" + dev.barboza.cofre.dominio.IdTransacao.ISPB_COFRE + ")",
                conta.getAgencia(), mascararConta(conta.getNumero()));
    }

    // ---------- Envio ----------

    @Transactional
    public Comprovante enviar(UsuarioLogado quem, String contaOrigem, String chaveDestino, BigDecimal valor,
            String mensagem, String chaveIdempotencia) {
        Conta origem = contas.buscarInterno(contaOrigem);
        Acesso.exigirDono(quem, origem);
        Optional<Comprovante> repetido = contas.repetida(origem, chaveIdempotencia, quem);
        if (repetido.isPresent()) {
            return repetido.get();
        }
        Conta destino = localizar(chaveDestino).getConta();
        if (destino.getNumero().equals(origem.getNumero())) {
            throw new OperacaoInvalidaException("Essa chave é da própria conta de origem.");
        }
        Instant agora = relogio.instant();
        BigDecimal valido = Dinheiro.validarValor(valor);
        Limites limites = limites(origem, agora);
        if (valido.compareTo(limites.disponivelNoPeriodo()) > 0) {
            throw new OperacaoInvalidaException((limites.noturno() ? "Limite do Pix noturno (20h às 6h)" : "Limite do Pix diurno")
                    + " excedido: disponível " + Dinheiro.formatar(limites.disponivelNoPeriodo()) + ".");
        }
        String id = contas.movimentar(origem, destino, valido, mensagem, TipoLancamento.PIX_ENVIADO,
                TipoLancamento.PIX_RECEBIDO, 'E', agora, chaveIdempotencia);
        auditoria.registrar(quem, "PIX_ENVIADO", origem.getNumero() + " -> " + destino.getNumero() + ": "
                + Dinheiro.formatar(valido));
        return contas.comprovanteInterno(id);
    }

    /** Envio sem checagens de sessão e limite, com data informada: usado nos dados de demonstração. */
    @Transactional
    public String enviarEm(String numeroOrigem, String chaveDestino, BigDecimal valor, String mensagem, Instant quando) {
        Conta origem = contas.buscarInterno(numeroOrigem);
        Conta destino = localizar(chaveDestino).getConta();
        return contas.movimentar(origem, destino, valor, mensagem, TipoLancamento.PIX_ENVIADO,
                TipoLancamento.PIX_RECEBIDO, 'E', quando, null);
    }

    @Transactional(readOnly = true)
    public Limites limites(UsuarioLogado quem, String numeroConta) {
        return limites(contas.buscar(quem, numeroConta), relogio.instant());
    }

    /** Período atual (diurno das 6h às 20h, noturno das 20h às 6h), quanto já foi usado e quanto resta. */
    private Limites limites(Conta conta, Instant agora) {
        ZonedDateTime local = agora.atZone(ContaService.FUSO);
        LocalDate dia = local.toLocalDate();
        int hora = local.getHour();
        ZonedDateTime inicio;
        ZonedDateTime fim;
        boolean noturno;
        if (hora >= 20) {
            inicio = dia.atTime(20, 0).atZone(ContaService.FUSO);
            fim = dia.plusDays(1).atTime(6, 0).atZone(ContaService.FUSO);
            noturno = true;
        } else if (hora < 6) {
            inicio = dia.minusDays(1).atTime(20, 0).atZone(ContaService.FUSO);
            fim = dia.atTime(6, 0).atZone(ContaService.FUSO);
            noturno = true;
        } else {
            inicio = dia.atTime(6, 0).atZone(ContaService.FUSO);
            fim = dia.atTime(20, 0).atZone(ContaService.FUSO);
            noturno = false;
        }
        BigDecimal limite = noturno ? LIMITE_NOTURNO : LIMITE_DIURNO;
        BigDecimal usado = lancamentos.somar(conta.getId(), List.of(TipoLancamento.PIX_ENVIADO),
                inicio.toInstant(), fim.toInstant()).setScale(2);
        return new Limites(limite, usado, limite.subtract(usado).max(BigDecimal.ZERO.setScale(2)), noturno, fim.toInstant());
    }

    // ---------- Cobrança (receber) ----------

    @Transactional(readOnly = true)
    public Cobranca cobrar(UsuarioLogado quem, String numeroConta, String chave, BigDecimal valor, String descricao) {
        Conta conta = contas.buscarInterno(numeroConta);
        Acesso.exigirDono(quem, conta);
        ChavePix escolhida = chaves.findByContaIdOrderByIdAsc(conta.getId()).stream()
                .filter(c -> chave == null || chave.isBlank() || c.getValor().equals(chave))
                .findFirst()
                .orElseThrow(() -> new OperacaoInvalidaException(chave == null || chave.isBlank()
                        ? "Cadastre uma chave Pix para poder receber." : "Essa chave não é desta conta."));
        BigDecimal valido = valor == null ? null : Dinheiro.validarValor(valor);
        String txid = "COFRE" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        String codigo = BrCode.gerar(new BrCode.Dados(escolhida.getValor(), conta.getCliente().getNome(), CIDADE,
                valido, txid, descricao));
        return new Cobranca(codigo, QrCodeSvg.gerar(codigo), valido, escolhida.getValor());
    }

    @Transactional(readOnly = true)
    public LeituraCopiaECola lerCopiaECola(String codigo) {
        BrCode.Dados dados = BrCode.ler(codigo);
        return new LeituraCopiaECola(consultar(dados.chave()), dados.valor(), dados.descricao(), dados.txid());
    }

    /** Paga um copia e cola. Se o código tem valor, ele prevalece sobre o informado. */
    @Transactional
    public Comprovante pagarCopiaECola(UsuarioLogado quem, String contaOrigem, String codigo, BigDecimal valorInformado,
            String chaveIdempotencia) {
        BrCode.Dados dados = BrCode.ler(codigo);
        BigDecimal valor = dados.valor() != null ? dados.valor() : valorInformado;
        String mensagem = dados.descricao() != null ? dados.descricao() : null;
        return enviar(quem, contaOrigem, dados.chave(), valor, mensagem, chaveIdempotencia);
    }

    // ---------- Apoio ----------

    /**
     * Acha a chave como o usuário digitou: aceita CPF com ou sem pontuação, e-mail em qualquer caixa,
     * celular com ou sem +55 e chave aleatória.
     */
    private ChavePix localizar(String digitada) {
        String texto = digitada == null ? "" : digitada.trim();
        if (texto.isEmpty()) {
            throw new OperacaoInvalidaException("Informe a chave Pix.");
        }
        Set<String> candidatos = new LinkedHashSet<>();
        candidatos.add(texto);
        candidatos.add(texto.toLowerCase());
        String digitos = texto.replaceAll("\\D", "");
        if (Cpf.valido(digitos)) {
            candidatos.add(digitos);
        }
        try {
            candidatos.add(Telefone.normalizar(texto));
        } catch (OperacaoInvalidaException e) {
            // não é celular
        }
        return chaves.findFirstByValorIn(candidatos)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chave Pix não encontrada."));
    }

    static String mascararConta(String numero) {
        return "***" + numero.substring(Math.max(0, numero.length() - 3));
    }

    /** Remove as chaves da conta (usado no encerramento). */
    @Transactional
    public void removerChavesDa(Conta conta) {
        chaves.deleteByContaId(conta.getId());
    }
}
