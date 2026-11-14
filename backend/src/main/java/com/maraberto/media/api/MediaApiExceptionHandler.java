package com.maraberto.media.api;

import com.maraberto.media.domain.ImageTooLargeException;
import com.maraberto.media.domain.InvalidImageException;
import com.maraberto.media.domain.MediaNotFoundException;
import com.maraberto.shared.api.ApiProblem;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Erros da mídia em RFC 9457, com {@code type} estável.
 */
@RestControllerAdvice(basePackageClasses = MediaController.class)
public class MediaApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final MediaType PROBLEM_JSON_UTF8 =
            new MediaType(MediaType.APPLICATION_PROBLEM_JSON, StandardCharsets.UTF_8);

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            org.springframework.web.multipart.MaxUploadSizeExceededException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return response(problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "https://maraberto.org/problems/image-too-large",
                "Arquivo grande demais",
                "A foto enviada passou do limite de upload.",
                request), headers);
    }

    @ExceptionHandler(ImageTooLargeException.class)
    public ResponseEntity<Object> handleTooLarge(
            ImageTooLargeException exception, WebRequest request) {
        return response(problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "https://maraberto.org/problems/image-too-large",
                "Arquivo grande demais",
                exception.getMessage(),
                request), new HttpHeaders());
    }

    @ExceptionHandler(InvalidImageException.class)
    public ResponseEntity<Object> handleInvalidImage(
            InvalidImageException exception, WebRequest request) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "https://maraberto.org/problems/invalid-image",
                "Imagem inválida",
                exception.getMessage(),
                request), new HttpHeaders());
    }

    @ExceptionHandler(MediaNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(
            MediaNotFoundException exception, WebRequest request) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "https://maraberto.org/problems/media-not-found",
                "Mídia não encontrada",
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
