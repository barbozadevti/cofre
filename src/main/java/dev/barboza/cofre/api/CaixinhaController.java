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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.barboza.cofre.api.Dtos.CaixinhaResposta;
import dev.barboza.cofre.api.Dtos.ValorRequisicao;
import dev.barboza.cofre.caixinha.CaixinhaService;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/caixinhas")
@Tag(name = "Caixinhas", description = "Dinheiro separado por objetivo")
public class CaixinhaController {

    private final CaixinhaService caixinhas;

    public CaixinhaController(CaixinhaService caixinhas) {
        this.caixinhas = caixinhas;
    }

    public record NovaCaixinha(@NotBlank(message = "Informe a conta.") String conta,
            @NotBlank(message = "Dê um nome à caixinha.") String nome, BigDecimal meta) {
    }

    @GetMapping
    public List<CaixinhaResposta> listar(@AuthenticationPrincipal UsuarioLogado u, @RequestParam String conta) {
        return caixinhas.listar(u, conta).stream().map(CaixinhaResposta::de).toList();
    }

    @PostMapping
    public ResponseEntity<CaixinhaResposta> criar(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody NovaCaixinha nova) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CaixinhaResposta.de(caixinhas.criar(u, nova.conta(), nova.nome(), nova.meta())));
    }

    @PostMapping("/{id}/guardar")
    public CaixinhaResposta guardar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id,
            @Valid @RequestBody ValorRequisicao req) {
        return CaixinhaResposta.de(caixinhas.guardar(u, id, req.valor()));
    }

    @PostMapping("/{id}/resgatar")
    public CaixinhaResposta resgatar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id,
            @Valid @RequestBody ValorRequisicao req) {
        return CaixinhaResposta.de(caixinhas.resgatar(u, id, req.valor()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
        caixinhas.excluir(u, id);
        return ResponseEntity.noContent().build();
    }
}
