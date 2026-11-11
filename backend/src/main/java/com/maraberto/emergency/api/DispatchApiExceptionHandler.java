package com.maraberto.emergency.api;

import com.maraberto.emergency.domain.CallAlreadyAcknowledgedException;
import com.maraberto.emergency.domain.DispatchNotAssignedException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Erros do despacho em RFC 9457, com {@code type} estável.
 *
 * <p>Deliberadamente não estende {@code ResponseEntityExceptionHandler}: o
 * {@link EmergencyApiExceptionHandler} já cobre os erros de MVC do pacote
 * {@code com.maraberto.emergency.api}. Duas classes com o mesmo tratamento
 * fariam o Spring escolher uma delas e devolver {@code about:blank} em vez dos
 * tipos estáveis.
 */
@RestControllerAdvice(basePackageClasses = DispatchController.class)
public class DispatchApiExceptionHandler {

    private static final MediaType PROBLEM_JSON_UTF8 =
            new MediaType(MediaType.APPLICATION_PROBLEM_JSON, StandardCharsets.UTF_8);

    @ExceptionHandler(CallAlreadyAcknowledgedException.class)
    public ResponseEntity<Object> handleAlreadyAcknowledged(
            CallAlreadyAcknowledgedException exception, WebRequest request) {
        return response(problem(
                HttpStatus.CONFLICT,
                "https://maraberto.org/problems/call-already-acknowledged",
                "Chamado já assumido",
                exception.getMessage(),
                request));
    }

    @ExceptionHandler(DispatchNotAssignedException.class)
    public ResponseEntity<Object> handleNotAssigned(
            DispatchNotAssignedException exception, WebRequest request) {
        return response(problem(
                HttpStatus.FORBIDDEN,
                "https://maraberto.org/problems/call-not-assigned",
                "Chamado não atribuído",
                exception.getMessage(),
                request));
    }

    private ResponseEntity<Object> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
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
