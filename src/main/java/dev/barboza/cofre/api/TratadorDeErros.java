package dev.barboza.cofre.api;

import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import dev.barboza.cofre.dominio.ContaNaoEncontradaException;
import dev.barboza.cofre.dominio.OperacaoInvalidaException;

/** Todos os erros da API saem como application/problem+json, em português. */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(ContaNaoEncontradaException.class)
    ProblemDetail naoEncontrada(ContaNaoEncontradaException e) {
        return problema(HttpStatus.NOT_FOUND, "Conta não encontrada", e.getMessage());
    }

    @ExceptionHandler(OperacaoInvalidaException.class)
    ProblemDetail operacaoInvalida(OperacaoInvalidaException e) {
        return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Operação não permitida", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacao(MethodArgumentNotValidException e) {
        String detalhe = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" "));
        return problema(HttpStatus.BAD_REQUEST, "Dados inválidos", detalhe);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail formato(Exception e) {
        return problema(HttpStatus.BAD_REQUEST, "Dados inválidos",
                "Não foi possível ler a requisição. Confira o JSON, os números (use ponto: 150.75) e as datas (AAAA-MM-DD).");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concorrencia(OptimisticLockingFailureException e) {
        return problema(HttpStatus.CONFLICT, "Conta alterada ao mesmo tempo",
                "A conta foi movimentada por outra operação no mesmo instante. Confira o saldo e tente de novo.");
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
