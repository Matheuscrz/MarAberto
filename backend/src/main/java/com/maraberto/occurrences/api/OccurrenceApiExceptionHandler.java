package com.maraberto.occurrences.api;

import com.maraberto.occurrences.domain.OccurrenceNotFoundException;
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
 * Erros das ocorrências em RFC 9457, com {@code type} estável.
 */
@RestControllerAdvice(basePackageClasses = OccurrenceController.class)
public class OccurrenceApiExceptionHandler extends ResponseEntityExceptionHandler {

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

    @ExceptionHandler(OccurrenceNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(
            OccurrenceNotFoundException exception, WebRequest request) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "https://maraberto.org/problems/occurrence-not-found",
                "Ocorrência não encontrada",
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
