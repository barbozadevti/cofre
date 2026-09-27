package dev.barboza.cofre.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.barboza.cofre.api.Dtos.ContaResposta;
import dev.barboza.cofre.auditoria.EventoAuditoria;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.servico.GerenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/gerencia")
@Tag(name = "Gerência", description = "Backoffice do gerente")
public class GerenciaController {

    private final GerenciaService gerencia;

    public GerenciaController(GerenciaService gerencia) {
        this.gerencia = gerencia;
    }

    public record NovaContaRequisicao(@NotBlank(message = "Informe o nome do cliente.") String nome,
            @NotBlank(message = "Informe o CPF.") String cpf,
            @NotBlank(message = "Informe o e-mail.") String email,
            String telefone, String agencia, BigDecimal depositoInicial, BigDecimal limite) {
    }

    public record ContaAbertaResposta(ContaResposta conta, String senhaProvisoria, String mensagem) {
    }

    public record Limite(@NotNull(message = "Informe o limite.") BigDecimal limite) {
    }

    public record Motivo(String motivo) {
    }

    public record SenhaProvisoria(String senhaProvisoria) {
    }

    public record EventoResposta(Instant dataHora, String usuario, String perfil, String acao, String detalhe, String origem) {
    }

    public record PaginaDeAuditoria(List<EventoResposta> eventos, int pagina, int totalDePaginas) {
    }

    @GetMapping("/indicadores")
    public GerenciaService.Indicadores indicadores(@AuthenticationPrincipal UsuarioLogado u) {
        return gerencia.indicadores(u);
    }

    @Operation(summary = "Abrir conta", description = "Cliente novo recebe acesso ao app com senha provisória (mostrada uma única vez).")
    @PostMapping("/contas")
    public ResponseEntity<ContaAbertaResposta> abrir(@AuthenticationPrincipal UsuarioLogado u,
            @Valid @RequestBody NovaContaRequisicao r) {
        GerenciaService.ContaAberta aberta = gerencia.abrirConta(u, new GerenciaService.NovaConta(r.nome(), r.cpf(),
                r.email(), r.telefone(), r.agencia(), r.depositoInicial(), r.limite()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new ContaAbertaResposta(ContaResposta.de(aberta.conta()),
                aberta.senhaProvisoria(), aberta.mensagem()));
    }

    @PutMapping("/contas/{numero}/limite")
    public ContaResposta limite(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero,
            @Valid @RequestBody Limite limite) {
        return ContaResposta.de(gerencia.definirLimite(u, numero, limite.limite()));
    }

    @PostMapping("/contas/{numero}/bloqueio")
    public ContaResposta bloquear(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero,
            @RequestBody Motivo motivo) {
        return ContaResposta.de(gerencia.bloquear(u, numero, motivo.motivo()));
    }

    @DeleteMapping("/contas/{numero}/bloqueio")
    public ContaResposta desbloquear(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero) {
        return ContaResposta.de(gerencia.desbloquear(u, numero));
    }

    @Operation(summary = "Encerrar conta", description = "Exige saldo e caixinhas zerados.")
    @DeleteMapping("/contas/{numero}")
    public ContaResposta encerrar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero) {
        return ContaResposta.de(gerencia.encerrar(u, numero));
    }

    @Operation(summary = "Redefinir senha do cliente", description = "Gera nova senha provisória e desbloqueia o acesso.")
    @PostMapping("/contas/{numero}/senha")
    public SenhaProvisoria redefinirSenha(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero) {
        return new SenhaProvisoria(gerencia.redefinirSenha(u, numero));
    }

    @GetMapping("/auditoria")
    public PaginaDeAuditoria auditoria(@AuthenticationPrincipal UsuarioLogado u, @RequestParam(defaultValue = "0") int pagina) {
        Page<EventoAuditoria> p = gerencia.auditoria(u, pagina);
        return new PaginaDeAuditoria(p.getContent().stream().map(e -> new EventoResposta(e.getDataHora(), e.getUsuario(),
                e.getPerfil(), e.getAcao(), e.getDetalhe(), e.getOrigem())).toList(), p.getNumber(), p.getTotalPages());
    }
}
