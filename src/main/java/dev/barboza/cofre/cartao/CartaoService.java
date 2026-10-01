package dev.barboza.cofre.cartao;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.cofre.auditoria.Auditoria;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import dev.barboza.cofre.dominio.ContaPoupanca;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.servico.Acesso;
import dev.barboza.cofre.servico.ContaService;

@Service
public class CartaoService {

    /** BIN fictício do Cofre (começa com 5, como a bandeira Mastercard). */
    static final String BIN = "529831";
    static final long JANELA_CVV_SEGUNDOS = 300;

    private static final SecureRandom SORTEIO = new SecureRandom();
    private static final DateTimeFormatter VALIDADE = DateTimeFormatter.ofPattern("MM/yy").withZone(ContaService.FUSO);

    private final CartaoRepository cartoes;
    private final ContaService contas;
    private final Auditoria auditoria;
    private final Clock relogio;
    private final byte[] segredo;

    public CartaoService(CartaoRepository cartoes, ContaService contas, Auditoria auditoria, Clock relogio,
            @Value("${cofre.cartao.segredo}") String segredo) {
        this.cartoes = cartoes;
        this.contas = contas;
        this.auditoria = auditoria;
        this.relogio = relogio;
        this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
    }

    public record DadosSensiveis(String numero, String validade, String cvv, Instant cvvValidoAte) {
    }

    /** Devolve o cartão virtual da conta, emitindo na primeira vez. */
    @Transactional
    public Cartao cartao(UsuarioLogado quem, String numeroConta) {
        Conta conta = contas.buscarInterno(numeroConta);
        Acesso.exigirDono(quem, conta);
        if (conta instanceof ContaPoupanca) {
            throw new OperacaoInvalidaException("O cartão de débito é da conta corrente.");
        }
        return cartoes.findByContaId(conta.getId()).orElseGet(() -> emitir(conta));
    }

    /** Mostra número completo e CVV (registrado na auditoria, como nos bancos). */
    @Transactional
    public DadosSensiveis revelar(UsuarioLogado quem, String numeroConta) {
        Cartao cartao = cartao(quem, numeroConta);
        Instant agora = relogio.instant();
        long janela = agora.getEpochSecond() / JANELA_CVV_SEGUNDOS;
        auditoria.registrar(quem, "CARTAO_DADOS_VISTOS", "Cartão final " + cartao.finalDoNumero());
        return new DadosSensiveis(cartao.getNumero(), cartao.getValidade(), cvv(cartao.getNumero(), janela),
                Instant.ofEpochSecond((janela + 1) * JANELA_CVV_SEGUNDOS));
    }

    @Transactional
    public Cartao definirBloqueio(UsuarioLogado quem, String numeroConta, boolean bloqueado) {
        Cartao cartao = cartao(quem, numeroConta);
        cartao.definirBloqueio(bloqueado);
        auditoria.registrar(quem, bloqueado ? "CARTAO_BLOQUEADO" : "CARTAO_DESBLOQUEADO", "Cartão final " + cartao.finalDoNumero());
        return cartao;
    }

    private Cartao emitir(Conta conta) {
        String numero;
        do {
            StringBuilder base = new StringBuilder(BIN);
            while (base.length() < 15) {
                base.append(SORTEIO.nextInt(10));
            }
            numero = base.toString() + Luhn.digito(base.toString());
        } while (cartoes.existsByNumero(numero));
        Instant agora = relogio.instant();
        String validade = VALIDADE.format(agora.atZone(ContaService.FUSO).plusYears(5));
        return cartoes.save(new Cartao(conta, numero, validade, agora));
    }

    /** CVV dinâmico: HMAC-SHA256 do número com a janela de 5 minutos, reduzido a 3 dígitos. */
    String cvv(String numero, long janela) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            byte[] hash = mac.doFinal((numero + ":" + janela).getBytes(StandardCharsets.UTF_8));
            int valor = ((hash[0] & 0x7F) << 24) | ((hash[1] & 0xFF) << 16) | ((hash[2] & 0xFF) << 8) | (hash[3] & 0xFF);
            return String.format("%03d", valor % 1000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
