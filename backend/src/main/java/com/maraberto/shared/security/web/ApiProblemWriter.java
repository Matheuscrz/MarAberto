package com.maraberto.shared.security.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Escreve respostas {@code application/problem+json} (RFC 9457) fora do MVC.
 *
 * <p>Usado pelo filtro JWT e pelos handlers de autenticação/autorização, que
 * respondem antes de existir {@code ProblemDetail} do Spring MVC. O corpo é um
 * record explícito para não depender da configuração do {@code ObjectMapper}.
 */
@Component
public class ApiProblemWriter {

    private final ObjectMapper objectMapper;

    public ApiProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String type,
            String title,
            String detail) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getOutputStream(),
                new ProblemResponse(type, status.value(), title, detail, request.getRequestURI()));
    }
}
