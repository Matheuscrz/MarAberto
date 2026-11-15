package com.maraberto.identity.application;

import com.maraberto.identity.domain.OriginNotAllowedException;
import com.maraberto.identity.infra.security.CookieProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Verifica a origem das rotas que dependem do cookie de refresh.
 *
 * <p>{@code SameSite=Strict} já impede que outro site apresente o cookie, mas
 * a checagem explícita fecha o espaço para clientes que montam a requisição
 * fora do navegador e para navegadores que não apliquem a política como
 * esperado. Ausência de {@code Origin} é aceita de propósito: o app Capacitor e
 * clientes de linha de comando não enviam o cabeçalho.
 */
@Component
public class OriginVerifier {

    private final CookieProperties properties;

    public OriginVerifier(CookieProperties properties) {
        this.properties = properties;
    }

    public void verify(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || origin.isBlank()) {
            return;
        }
        if (!properties.getAllowedOrigins().contains(origin)) {
            throw new OriginNotAllowedException();
        }
    }
}
