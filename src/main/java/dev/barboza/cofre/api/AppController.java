package dev.barboza.cofre.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.barboza.cofre.api.Dtos.ContaResposta;
import dev.barboza.cofre.api.Dtos.ExtratoResposta;
import dev.barboza.cofre.api.Dtos.LancamentoResposta;
import dev.barboza.cofre.api.Dtos.PainelResposta;
import dev.barboza.cofre.api.Dtos.TransferenciaRequisicao;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.servico.Comprovante;
import dev.barboza.cofre.servico.ContaService;
import dev.barboza.cofre.servico.Extrato;
import dev.barboza.cofre.servico.PainelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** App do cliente: painel, contas, extrato, transferências e comprovantes. */
@RestController
@RequestMapping("/api/app")
@Tag(name = "App do cliente")
public class AppController {

    private final ContaService contas;
    private final PainelService painel;

    public AppController(ContaService contas, PainelService painel) {
        this.contas = contas;
        this.painel = painel;
    }

    @Operation(summary = "Tela inicial", description = "Saldos, cheque especial, caixinhas e entradas x saídas dos últimos 6 meses.")
    @GetMapping("/painel")
    public PainelResposta painel(@AuthenticationPrincipal UsuarioLogado u) {
        PainelService.Painel p = painel.painel(u);
        return new PainelResposta(u.nome(), p.saldoTotal(), p.limiteTotal(), p.limiteEmUso(), p.emCaixinhas(),
                p.contas().stream().map(ContaResposta::de).toList(), p.meses());
    }

    @GetMapping("/contas")
    public List<ContaResposta> contas(@AuthenticationPrincipal UsuarioLogado u) {
        return contas.contasVisiveis(u).stream().map(ContaResposta::de).toList();
    }

    @Operation(summary = "Abrir minha poupança", description = "Uma por cliente, na mesma agência. Rende 0,5% ao mês no aniversário.")
    @PostMapping("/poupanca")
    public ResponseEntity<ContaResposta> abrirPoupanca(@AuthenticationPrincipal UsuarioLogado u) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(ContaResposta.de(contas.abrirMinhaPoupanca(u)));
    }

    @GetMapping("/contas/{numero}/ultimos")
    public List<LancamentoResposta> ultimos(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero) {
        return contas.ultimos(u, numero).stream().map(LancamentoResposta::de).toList();
    }

    @Operation(summary = "Extrato", description = "Sem datas, mostra os últimos 30 dias. Período máximo de 1 ano.")
    @GetMapping("/contas/{numero}/extrato")
    public ExtratoResposta extrato(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return ExtratoResposta.de(contas.extrato(u, numero, de, ate));
    }

    @Operation(summary = "Extrato em CSV", description = "UTF-8 com BOM, separador ';' e vírgula decimal, pronto para o Excel.")
    @GetMapping("/contas/{numero}/extrato.csv")
    public ResponseEntity<byte[]> extratoCsv(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        Extrato extrato = contas.extrato(u, numero, de, ate);
        String nome = "extrato-" + extrato.conta().getNumero() + "-" + extrato.de() + "-a-" + extrato.ate() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(ExtratoCsv.gerar(extrato).getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "Transferir entre contas do Cofre",
            description = "Envie o cabeçalho Idempotency-Key para que um reenvio (ex.: clique duplo) não duplique a transferência.")
    @PostMapping("/transferencias")
    public Comprovante transferir(@AuthenticationPrincipal UsuarioLogado u,
            @RequestHeader(value = "Idempotency-Key", required = false) String chave,
            @Valid @RequestBody TransferenciaRequisicao req) {
        return contas.transferir(u, req.origem(), req.destino(), req.valor(), req.mensagem(), chave);
    }

    @GetMapping("/comprovantes/{idTransacao}")
    public Comprovante comprovante(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String idTransacao) {
        return contas.comprovante(u, idTransacao);
    }
}
