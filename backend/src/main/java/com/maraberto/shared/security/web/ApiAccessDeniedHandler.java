package com.maraberto.shared.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 403 em RFC 9457 quando o operador autenticado não tem o papel exigido.
 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    public static final String PROBLEM_TYPE = "https://maraberto.org/problems/forbidden";

    private final ApiProblemWriter writer;

    public ApiAccessDeniedHandler(ApiProblemWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        writer.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                PROBLEM_TYPE,
                "Acesso negado",
                "O perfil deste operador não tem permissão para esta operação.");
    }
}
