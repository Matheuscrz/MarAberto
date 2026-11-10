package com.maraberto.shared.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 401 em RFC 9457 para requisições sem token válido.
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String PROBLEM_TYPE = "https://maraberto.org/problems/unauthorized";

    private final ApiProblemWriter writer;

    public ApiAuthenticationEntryPoint(ApiProblemWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        writer.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                PROBLEM_TYPE,
                "Não autenticado",
                "Envie um token de acesso válido em Authorization: Bearer.");
    }
}
