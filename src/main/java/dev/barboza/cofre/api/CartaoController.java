package dev.barboza.cofre.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.barboza.cofre.api.Dtos.CartaoResposta;
import dev.barboza.cofre.cartao.CartaoService;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/cartao/{conta}")
@Tag(name = "Cartão virtual", description = "Cartão de débito virtual com CVV dinâmico")
public class CartaoController {

    private final CartaoService cartoes;

    public CartaoController(CartaoService cartoes) {
        this.cartoes = cartoes;
    }

    public record Bloqueio(boolean bloqueado) {
    }

    @GetMapping
    public CartaoResposta cartao(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String conta) {
        return CartaoResposta.de(cartoes.cartao(u, conta));
    }

    @Operation(summary = "Ver dados do cartão", description = "Número completo e CVV dinâmico (muda a cada 5 minutos). Fica registrado na auditoria.")
    @PostMapping("/dados")
    public CartaoService.DadosSensiveis revelar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String conta) {
        return cartoes.revelar(u, conta);
    }

    @PutMapping("/bloqueio")
    public CartaoResposta bloquear(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String conta,
            @RequestBody Bloqueio bloqueio) {
        return CartaoResposta.de(cartoes.definirBloqueio(u, conta, bloqueio.bloqueado()));
    }
}
