package dev.barboza.cofre.auditoria;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import dev.barboza.cofre.seguranca.UsuarioLogado;

@Service
public class Auditoria {

    private final AuditoriaRepository eventos;
    private final Clock relogio;

    public Auditoria(AuditoriaRepository eventos, Clock relogio) {
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /** Registra dentro da transação da operação: se a operação falhar, o registro também não fica. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(UsuarioLogado quem, String acao, String detalhe) {
        eventos.save(new EventoAuditoria(relogio.instant(), quem.login(), quem.perfil().name(), acao, detalhe, origem()));
    }

    /** Registra em transação própria: usado em falhas (ex.: senha errada), que precisam ficar gravadas. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAvulso(String usuario, String perfil, String acao, String detalhe) {
        eventos.save(new EventoAuditoria(relogio.instant(), usuario, perfil, acao, detalhe, origem()));
    }

    private static String origem() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            return atributos.getRequest().getRemoteAddr();
        }
        return "terminal";
    }
}
