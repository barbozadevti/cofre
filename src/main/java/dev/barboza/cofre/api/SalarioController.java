package dev.barboza.cofre.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

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
import org.springframework.web.bind.annotation.RestController;

import dev.barboza.cofre.salario.BancoDeOrigem;
import dev.barboza.cofre.salario.Cnpj;
import dev.barboza.cofre.salario.PortabilidadeSalario;
import dev.barboza.cofre.salario.SalarioService;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/app/salario")
@Tag(name = "Traga seu salário", description = "Portabilidade de salário (simulada) e Pague-se primeiro")
public class SalarioController {

    private final SalarioService salario;

    public SalarioController(SalarioService salario) {
        this.salario = salario;
    }

    public record Banco(String codigo, String nome) {
    }

    public record EtapaResposta(String nome, Instant quando, boolean feita) {
    }

    public record PortabilidadeResposta(Long id, String protocolo, String banco, String bancoNome, String empregador,
            String cnpj, BigDecimal salario, int diaPagamento, String situacao, String situacaoNome, Instant solicitadaEm,
            Instant concluidaEm, List<EtapaResposta> etapas) {

        static PortabilidadeResposta de(PortabilidadeSalario p, Instant agora) {
            PortabilidadeSalario.Situacao s = p.situacao(agora);
            return new PortabilidadeResposta(p.getId(), p.getProtocolo(), p.banco().codigo(), p.banco().nome(), p.getEmpregador(),
                    Cnpj.formatar(p.getCnpjEmpregador()), p.getSalario(), p.getDiaPagamento(), s.name(), s.nome(),
                    p.getSolicitadaEm(), p.concluidaEm(),
                    p.etapas(agora).stream().map(e -> new EtapaResposta(e.nome(), e.quando(), e.feita())).toList());
        }
    }

    public record SalarioResposta(String conta, int reservaPercentual, boolean temPoupanca, BigDecimal salariosRecebidos,
            long mesesRecebidos, List<PortabilidadeResposta> portabilidades, List<Banco> bancos) {

        static SalarioResposta de(SalarioService.Resumo r) {
            return new SalarioResposta(r.corrente().getNumero(), r.corrente().getReservaPercentual(), r.temPoupanca(),
                    r.salariosRecebidos(), r.mesesRecebidos(),
                    r.portabilidades().stream().map(p -> PortabilidadeResposta.de(p, r.agora())).toList(),
                    Arrays.stream(BancoDeOrigem.values()).map(b -> new Banco(b.codigo(), b.nome())).toList());
        }
    }

    public record Reserva(@NotNull Integer percentual) {
    }

    @GetMapping
    public SalarioResposta ver(@AuthenticationPrincipal UsuarioLogado u) {
        return SalarioResposta.de(salario.resumo(u));
    }

    @Operation(summary = "Pedir a portabilidade de salário",
            description = "Simulação: o banco de origem recebe o pedido em 1 dia e conclui em 3. Depois, o salário cai no Cofre no dia do pagamento.")
    @PostMapping("/portabilidade")
    public ResponseEntity<SalarioResposta> solicitar(@AuthenticationPrincipal UsuarioLogado u, @RequestBody SalarioService.Pedido pedido) {
        salario.solicitar(u, pedido);
        return ResponseEntity.status(HttpStatus.CREATED).body(SalarioResposta.de(salario.resumo(u)));
    }

    @DeleteMapping("/portabilidade/{id}")
    public SalarioResposta cancelar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
        salario.cancelar(u, id);
        return SalarioResposta.de(salario.resumo(u));
    }

    @Operation(summary = "Pague-se primeiro", description = "Porcentagem do salário (0 a 50) guardada na poupança assim que ele cai.")
    @PutMapping("/reserva")
    public SalarioResposta reserva(@AuthenticationPrincipal UsuarioLogado u, @RequestBody Reserva reserva) {
        salario.definirReserva(u, reserva.percentual() == null ? 0 : reserva.percentual());
        return SalarioResposta.de(salario.resumo(u));
    }
}
