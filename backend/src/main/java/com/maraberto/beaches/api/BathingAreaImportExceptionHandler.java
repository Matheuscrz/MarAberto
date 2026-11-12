package com.maraberto.beaches.api;

import com.maraberto.beaches.application.ImportRejectedException;
import com.maraberto.beaches.application.KmlParseException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Erros da importação de áreas de banho em RFC 9457. */
@RestControllerAdvice(basePackageClasses = BathingAreaImportController.class)
public class BathingAreaImportExceptionHandler {

    @ExceptionHandler({ImportRejectedException.class, KmlParseException.class})
    public ResponseEntity<Object> handleInvalidImport(
            RuntimeException exception, WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "importacao-invalida",
                "Importação inválida", exception.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> handleTooLarge(
            MaxUploadSizeExceededException exception, WebRequest request) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "arquivo-muito-grande",
                "Arquivo muito grande", "O KML deve ter no máximo 5 MB.", request);
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
