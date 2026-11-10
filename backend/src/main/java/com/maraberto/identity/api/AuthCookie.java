package com.maraberto.identity.api;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * Cookie do refresh token (ADR-009).
 *
 * <p>O nome é fixo: {@code __Secure-} exige HTTPS (localhost é tratado como
 * contexto seguro pelos navegadores) e {@code SameSite=Strict} impede que uma
 * requisição de outro site apresente o cookie. {@code Path=/api/v1/auth} evita
 * enviar o refresh em qualquer outra chamada da API. O JavaScript nunca lê o
 * valor ({@code HttpOnly}).
 */
public final class AuthCookie {

    public static final String NAME = "__Secure-maraberto_rt";
    public static final String PATH = "/api/v1/auth";

    private AuthCookie() {
    }

    public static ResponseCookie create(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }

    public static ResponseCookie clear() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}
