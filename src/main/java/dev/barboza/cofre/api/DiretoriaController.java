package dev.barboza.cofre.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.barboza.cofre.diretoria.DiretoriaService;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/diretoria")
@Tag(name = "Diretoria", description = "Visão executiva do banco (perfil DIRETORIA, só leitura)")
public class DiretoriaController {

    private final DiretoriaService diretoria;

    public DiretoriaController(DiretoriaService diretoria) {
        this.diretoria = diretoria;
    }

    @Operation(summary = "Painel executivo", description = "Custódia, resultado de juros, crédito, Pix, antifraude, Escudo, "
            + "portabilidade de salário, concentração e evolução de 6 meses, com uma leitura em texto.")
    @GetMapping("/painel")
    public DiretoriaService.Painel painel(@AuthenticationPrincipal UsuarioLogado u) {
        return diretoria.painel(u);
    }
}
