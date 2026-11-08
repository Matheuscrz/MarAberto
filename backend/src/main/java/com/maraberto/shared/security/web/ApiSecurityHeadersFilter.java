package com.maraberto.shared.security.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cabeçalhos de segurança para respostas da API.
 *
 * <p>A borda (Nginx) também aplica cabeçalhos; esta camada garante o mesmo
 * contrato quando a API é exposta diretamente ou atrás de outro proxy. HSTS só
 * é enviado quando a requisição é HTTPS, para não quebrar o ambiente local.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiSecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CSP_API =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "geolocation=(self), camera=(), microphone=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Resource-Policy", "same-site");
        response.setHeader("Content-Security-Policy", CSP_API);

        if (isSecure(request)) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/emergency-calls") || path.startsWith("/api/v1/auth")) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSecure(HttpServletRequest request) {
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }
}
