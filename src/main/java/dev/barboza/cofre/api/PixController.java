package dev.barboza.cofre.api;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.barboza.cofre.api.Dtos.ChaveResposta;
import dev.barboza.cofre.pix.PixService;
import dev.barboza.cofre.pix.TipoChavePix;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.servico.Comprovante;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/pix")
@Tag(name = "Pix", description = "Chaves, envio, cobrança por QR Code (BR Code) e copia e cola")
public class PixController {

    private final PixService pix;

    public PixController(PixService pix) {
        this.pix = pix;
    }

    public record NovaChave(@NotNull(message = "Escolha o tipo da chave.") TipoChavePix tipo, String valor) {
    }

    public record Envio(@NotBlank(message = "Informe a conta de origem.") String conta,
            @NotBlank(message = "Informe a chave Pix.") String chave,
            @NotNull(message = "Informe o valor.") BigDecimal valor,
            String mensagem) {
    }

    public record PedidoDeCobranca(@NotBlank(message = "Informe a conta.") String conta, String chave, BigDecimal valor,
            String descricao) {
    }

    public record CodigoCopiaECola(@NotBlank(message = "Cole o código Pix.") String codigo) {
    }

    public record PagamentoCopiaECola(@NotBlank(message = "Informe a conta de origem.") String conta,
            @NotBlank(message = "Cole o código Pix.") String codigo, BigDecimal valor) {
    }

    @GetMapping("/contas/{numero}/chaves")
    public List<ChaveResposta> chaves(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero) {
        return pix.chaves(u, numero).stream().map(ChaveResposta::de).toList();
    }

    @Operation(summary = "Cadastrar chave", description = "CPF (do titular), e-mail, celular ou aleatória. Máximo de 5 por conta.")
    @PostMapping("/contas/{numero}/chaves")
    public ResponseEntity<ChaveResposta> cadastrar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero,
            @Valid @RequestBody NovaChave nova) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ChaveResposta.de(pix.cadastrar(u, numero, nova.tipo(), nova.valor())));
    }

    @DeleteMapping("/chaves/{id}")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
        pix.excluir(u, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Consultar destinatário", description = "Mostra nome e CPF mascarado antes de confirmar o envio.")
    @GetMapping("/destinatarios")
    public PixService.Destinatario consultar(@RequestParam String chave) {
        return pix.consultar(chave);
    }

    @GetMapping("/contas/{numero}/limites")
    public PixService.Limites limites(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero) {
        return pix.limites(u, numero);
    }

    @Operation(summary = "Enviar Pix", description = "Limite noturno (20h às 6h) de R$ 1.000,00. Use Idempotency-Key para evitar "
            + "envio duplicado. Com nota de risco alta, responde 428 com os motivos; para enviar mesmo assim, repita com "
            + "o cabeçalho X-Confirmacao-Risco: confirmo.")
    @PostMapping("/envios")
    public Comprovante enviar(@AuthenticationPrincipal UsuarioLogado u,
            @RequestHeader(value = "Idempotency-Key", required = false) String chave,
            @RequestHeader(value = "X-Confirmacao-Risco", required = false) String confirmacao, @Valid @RequestBody Envio envio) {
        return pix.enviar(u, envio.conta(), envio.chave(), envio.valor(), envio.mensagem(), chave, "confirmo".equals(confirmacao));
    }

    @Operation(summary = "Gerar cobrança", description = "Devolve o código copia e cola (BR Code com CRC16) e o QR Code em SVG.")
    @PostMapping("/cobrancas")
    public PixService.Cobranca cobrar(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody PedidoDeCobranca pedido) {
        return pix.cobrar(u, pedido.conta(), pedido.chave(), pedido.valor(), pedido.descricao());
    }

    @Operation(summary = "Ler copia e cola", description = "Confere o código e mostra quem vai receber e quanto.")
    @PostMapping("/copia-e-cola/leitura")
    public PixService.LeituraCopiaECola ler(@Valid @RequestBody CodigoCopiaECola codigo) {
        return pix.lerCopiaECola(codigo.codigo());
    }

    @PostMapping("/copia-e-cola/pagamento")
    public Comprovante pagar(@AuthenticationPrincipal UsuarioLogado u,
            @RequestHeader(value = "Idempotency-Key", required = false) String chave,
            @RequestHeader(value = "X-Confirmacao-Risco", required = false) String confirmacao,
            @Valid @RequestBody PagamentoCopiaECola pagamento) {
        return pix.pagarCopiaECola(u, pagamento.conta(), pagamento.codigo(), pagamento.valor(), chave, "confirmo".equals(confirmacao));
    }
}
