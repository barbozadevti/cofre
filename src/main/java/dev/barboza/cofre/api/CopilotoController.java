package dev.barboza.cofre.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.barboza.cofre.copiloto.CopilotoService;
import dev.barboza.cofre.copiloto.PrevisaoDeSaldo;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Copiloto financeiro: previsão de saldo e Escudo de juros. */
@RestController
@RequestMapping("/api/app/contas/{numero}/copiloto")
@Tag(name = "Copiloto financeiro", description = "Previsão de saldo para 30 dias e Escudo de juros")
public class CopilotoController {

    private final CopilotoService copiloto;

    public CopilotoController(CopilotoService copiloto) {
        this.copiloto = copiloto;
    }

    public record EventoResposta(LocalDate dia, String descricao, BigDecimal valor, BigDecimal saldoApos) {
    }

    public record PontoResposta(LocalDate dia, BigDecimal saldo) {
    }

    public record RecorrenciaResposta(String descricao, int dia, BigDecimal valor, int meses) {
    }

    public record CopilotoResposta(String nivel, String titulo, String mensagem, BigDecimal saldoAtual, BigDecimal menorSaldo,
            LocalDate diaDoMenorSaldo, LocalDate primeiroDiaNegativo, List<PontoResposta> pontos, List<EventoResposta> eventos,
            List<RecorrenciaResposta> recorrencias, CopilotoService.Escudo escudo) {

        static CopilotoResposta de(CopilotoService.Copiloto c) {
            PrevisaoDeSaldo.Resultado p = c.previsao();
            return new CopilotoResposta(c.nivel().name(), c.titulo(), c.mensagem(), p.saldoAtual(), p.menorSaldo(),
                    p.diaDoMenorSaldo(), p.primeiroDiaNegativo(),
                    p.pontos().stream().map(x -> new PontoResposta(x.dia(), x.saldo())).toList(),
                    p.eventos().stream().map(e -> new EventoResposta(e.dia(), e.descricao(), e.valor(), e.saldoApos())).toList(),
                    p.recorrencias().stream().map(r -> new RecorrenciaResposta(r.descricao(), r.dia(), r.valor(), r.meses())).toList(),
                    c.escudo());
        }
    }

    @Operation(summary = "Previsão de saldo e situação do Escudo",
            description = "Detecta o que se repete todo mês (salário, aluguel...) nos últimos 3 meses e projeta os próximos 30 dias.")
    @GetMapping
    public CopilotoResposta ver(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero) {
        return CopilotoResposta.de(copiloto.copiloto(u, numero));
    }

    @Operation(summary = "Ligar o Escudo de juros", description = "O saldo negativo passa a ser coberto com a poupança, sem juros. Cobre na hora se já estiver negativo.")
    @PostMapping("/escudo")
    public CopilotoResposta ativar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero) {
        return CopilotoResposta.de(copiloto.ativarEscudo(u, numero));
    }

    @Operation(summary = "Desligar o Escudo de juros")
    @DeleteMapping("/escudo")
    public CopilotoResposta desativar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable String numero) {
        return CopilotoResposta.de(copiloto.desativarEscudo(u, numero));
    }
}
