package com.maraberto.beaches.api;

import com.maraberto.beaches.application.BeachNotFoundException;
import com.maraberto.beaches.application.InvalidLocationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/** Erros da consulta pública de praias em RFC 9457. */
@RestControllerAdvice(basePackageClasses = PublicBeachController.class)
public class BeachQueryExceptionHandler {

    @ExceptionHandler(InvalidLocationException.class)
    public ResponseEntity<Object> handleInvalidLocation(
            InvalidLocationException exception, WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "localizacao-invalida",
                "Consulta inválida", exception.getMessage(), request);
    }

    @ExceptionHandler(BeachNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(
            BeachNotFoundException exception, WebRequest request) {
        return problem(HttpStatus.NOT_FOUND, "praia-nao-encontrada",
                "Área de banho não encontrada", exception.getMessage(), request);
    }

    private ResponseEntity<Object> problem(
            HttpStatus status, String type, String title, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://maraberto.org/problems/" + type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
