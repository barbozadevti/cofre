package dev.barboza.cofre.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.barboza.cofre.api.Dtos.ContaResposta;
import dev.barboza.cofre.api.Dtos.ExtratoResposta;
import dev.barboza.cofre.api.Dtos.ValorRequisicao;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.servico.ContaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Balcão da agência (caixa e gerente): consulta de contas e operações em espécie. */
@RestController
@RequestMapping("/api/agencia")
@Tag(name = "Agência", description = "Caixa e gerente: consulta e operações em espécie")
public class AgenciaController {

    private final ContaService contas;

    public AgenciaController(ContaService contas) {
        this.contas = contas;
    }

    @Operation(summary = "Buscar contas", description = "Por nome, CPF ou número da conta.")
    @GetMapping("/contas")
    public List<ContaResposta> buscar(@AuthenticationPrincipal UsuarioLogado u, @RequestParam(required = false) String busca) {
        return contas.pesquisar(u, busca).stream().map(ContaResposta::de).toList();
    }

    @GetMapping("/contas/{numero}")
    public ContaResposta conta(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero) {
        return ContaResposta.de(contas.buscar(u, numero));
    }

    @PostMapping("/contas/{numero}/depositos")
    public ContaResposta depositar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero,
            @Valid @RequestBody ValorRequisicao req) {
        return ContaResposta.de(contas.depositarEmEspecie(u, numero, req.valor()));
    }

    @Operation(summary = "Saque em espécie", description = "Pode usar o cheque especial do cliente.")
    @PostMapping("/contas/{numero}/saques")
    public ContaResposta sacar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero,
            @Valid @RequestBody ValorRequisicao req) {
        return ContaResposta.de(contas.sacarEmEspecie(u, numero, req.valor()));
    }

    @GetMapping("/contas/{numero}/extrato")
    public ExtratoResposta extrato(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return ExtratoResposta.de(contas.extrato(u, numero, de, ate));
    }
}
