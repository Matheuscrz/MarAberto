package com.maraberto.operations.api;

import com.maraberto.operations.domain.ShiftAlreadyOpenException;
import com.maraberto.operations.domain.ShiftNotFoundException;
import com.maraberto.shared.api.FieldValidationError;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Erros do plantão em RFC 9457, com {@code type} estável.
 */
@RestControllerAdvice(basePackageClasses = ShiftController.class)
public class OperationsApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final MediaType PROBLEM_JSON_UTF8 =
            new MediaType(MediaType.APPLICATION_PROBLEM_JSON, StandardCharsets.UTF_8);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "https://maraberto.org/problems/invalid-request",
                "Requisição inválida",
                "Um ou mais campos não atendem às regras de validação.",
                request);
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldValidationError(error.getField(), error.getDefaultMessage()))
                .toList());
        return response(problem, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "https://maraberto.org/problems/malformed-json",
                "JSON inválido",
                "O corpo da requisição não pôde ser interpretado.",
                request), headers);
    }

    @ExceptionHandler(ShiftAlreadyOpenException.class)
    public ResponseEntity<Object> handleAlreadyOpen(
            ShiftAlreadyOpenException exception, WebRequest request) {
        return response(problem(
                HttpStatus.CONFLICT,
                "https://maraberto.org/problems/shift-already-open",
                "Plantão já aberto",
                exception.getMessage(),
                request), new HttpHeaders());
    }

    @ExceptionHandler(ShiftNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(
            ShiftNotFoundException exception, WebRequest request) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "https://maraberto.org/problems/shift-not-found",
                "Plantão não encontrado",
                exception.getMessage(),
                request), new HttpHeaders());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleInvalid(
            IllegalArgumentException exception, WebRequest request) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "https://maraberto.org/problems/invalid-request",
                "Requisição inválida",
                exception.getMessage(),
                request), new HttpHeaders());
    }

    private ResponseEntity<Object> response(ProblemDetail problem, HttpHeaders headers) {
        return ResponseEntity.status(problem.getStatus())
                .headers(headers)
                .contentType(PROBLEM_JSON_UTF8)
                .body(problem);
    }

    private ProblemDetail problem(
            HttpStatus status, String type, String title, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        return problem;
    }
}
