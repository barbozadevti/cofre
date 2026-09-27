package dev.barboza.cofre.api;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.barboza.cofre.api.Dtos.AberturaRequisicao;
import dev.barboza.cofre.api.Dtos.AberturaResposta;
import dev.barboza.cofre.api.Dtos.ContaResposta;
import dev.barboza.cofre.api.Dtos.ExtratoResposta;
import dev.barboza.cofre.api.Dtos.TransferenciaRequisicao;
import dev.barboza.cofre.api.Dtos.TransferenciaResposta;
import dev.barboza.cofre.api.Dtos.ValorRequisicao;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.servico.ContaService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ContaController {

    private final ContaService servico;

    public ContaController(ContaService servico) {
        this.servico = servico;
    }

    @GetMapping("/contas")
    public List<ContaResposta> listar() {
        return servico.listar().stream().map(ContaResposta::de).toList();
    }

    @PostMapping("/contas")
    public ResponseEntity<AberturaResposta> abrir(@Valid @RequestBody AberturaRequisicao req) {
        Conta conta = servico.abrir(req.titular(), req.agencia(), req.saldoInicial());
        return ResponseEntity.created(URI.create("/api/contas/" + conta.getNumero()))
                .body(new AberturaResposta(ContaResposta.de(conta), conta.mensagemDeBoasVindas()));
    }

    @GetMapping("/contas/{numero}")
    public ContaResposta buscar(@PathVariable String numero) {
        return ContaResposta.de(servico.buscar(numero));
    }

    @PostMapping("/contas/{numero}/depositos")
    public ContaResposta depositar(@PathVariable String numero, @Valid @RequestBody ValorRequisicao req) {
        return ContaResposta.de(servico.depositar(numero, req.valor()));
    }

    @PostMapping("/contas/{numero}/saques")
    public ContaResposta sacar(@PathVariable String numero, @Valid @RequestBody ValorRequisicao req) {
        return ContaResposta.de(servico.sacar(numero, req.valor()));
    }

    @PostMapping("/transferencias")
    public TransferenciaResposta transferir(@Valid @RequestBody TransferenciaRequisicao req) {
        ContaService.Transferencia t = servico.transferir(req.origem(), req.destino(), req.valor());
        return new TransferenciaResposta(ContaResposta.de(t.origem()), ContaResposta.de(t.destino()));
    }

    @DeleteMapping("/contas/{numero}")
    public ContaResposta encerrar(@PathVariable String numero) {
        return ContaResposta.de(servico.encerrar(numero));
    }

    @GetMapping("/contas/{numero}/extrato")
    public ExtratoResposta extrato(
            @PathVariable String numero,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return ExtratoResposta.de(servico.extrato(numero, de, ate));
    }

    /** Extrato em CSV para o Excel (UTF-8 com BOM, separador ";" e vírgula decimal). */
    @GetMapping("/contas/{numero}/extrato.csv")
    public ResponseEntity<byte[]> extratoCsv(
            @PathVariable String numero,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        var extrato = servico.extrato(numero, de, ate);
        String nome = "extrato-" + extrato.conta().getNumero() + "-" + extrato.de() + "-a-" + extrato.ate() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(ExtratoCsv.gerar(extrato).getBytes(StandardCharsets.UTF_8));
    }
}
