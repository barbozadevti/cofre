package dev.barboza.cofre.api;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import dev.barboza.cofre.dominio.AcessoNegadoException;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;
import dev.barboza.cofre.dominio.RecursoNaoEncontradoException;
import dev.barboza.cofre.seguranca.CredenciaisInvalidasException;
import dev.barboza.cofre.seguranca.UsuarioBloqueadoException;

/** Todos os erros da API saem como application/problem+json (RFC 9457), em português. */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Não encontrado", e.getMessage());
    }

    @ExceptionHandler(OperacaoInvalidaException.class)
    ProblemDetail operacaoInvalida(OperacaoInvalidaException e) {
        return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Operação não permitida", e.getMessage());
    }

    /** 428: o Pix precisa de confirmação reforçada. A resposta traz a nota e os motivos para a tela mostrar. */
    @ExceptionHandler(dev.barboza.cofre.pix.ConfirmacaoDeRiscoException.class)
    ProblemDetail confirmacaoDeRisco(dev.barboza.cofre.pix.ConfirmacaoDeRiscoException e) {
        ProblemDetail p = problema(HttpStatus.PRECONDITION_REQUIRED, "Confirme este Pix", e.getMessage());
        p.setProperty("pontuacao", e.getAvaliacao().pontuacao());
        p.setProperty("fatores", e.getAvaliacao().fatores());
        return p;
    }

    @ExceptionHandler(AcessoNegadoException.class)
    ProblemDetail acessoNegado(AcessoNegadoException e) {
        return problema(HttpStatus.FORBIDDEN, "Acesso negado", e.getMessage());
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    ProblemDetail credenciais(CredenciaisInvalidasException e) {
        return problema(HttpStatus.UNAUTHORIZED, "Não foi possível entrar", e.getMessage());
    }

    @ExceptionHandler(UsuarioBloqueadoException.class)
    ProblemDetail bloqueado(UsuarioBloqueadoException e) {
        return problema(HttpStatus.LOCKED, "Acesso bloqueado", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacao(MethodArgumentNotValidException e) {
        String detalhe = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" "));
        return problema(HttpStatus.BAD_REQUEST, "Dados inválidos", detalhe);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ProblemDetail formato(Exception e) {
        return problema(HttpStatus.BAD_REQUEST, "Dados inválidos",
                "Não foi possível ler a requisição. Confira o JSON, os números (use ponto: 150.75) e as datas (AAAA-MM-DD).");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concorrencia(OptimisticLockingFailureException e) {
        return problema(HttpStatus.CONFLICT, "Conta alterada ao mesmo tempo",
                "A conta foi movimentada por outra operação no mesmo instante. Confira o saldo e tente de novo.");
    }

    /** Ex.: duas requisições simultâneas com a mesma Idempotency-Key ou a mesma chave Pix. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflito(DataIntegrityViolationException e) {
        return problema(HttpStatus.CONFLICT, "Operação em conflito",
                "Esta operação conflita com outra feita ao mesmo tempo. Confira e tente de novo.");
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
