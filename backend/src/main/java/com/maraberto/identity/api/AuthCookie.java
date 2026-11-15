package com.maraberto.identity.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * Cookie do refresh token (ADR-009).
 *
 * <p>Em HTTPS o nome leva {@code __Secure-}: o prefixo impede que um site em
 * HTTP sobrescreva o cookie. Quando {@code maraberto.security.cookie.secure}
 * está desligado (só desenvolvimento em HTTP) o prefixo sai de cena, porque o
 * navegador rejeitaria um cookie {@code __Secure-} sem o atributo
 * {@code Secure}. {@code SameSite=Strict} e {@code Path=/api/v1/auth} valem
 * nos dois casos; o JavaScript nunca lê o valor ({@code HttpOnly}).
 */
public final class AuthCookie {

    /** Nome usado em HTTPS. */
    public static final String NAME = "__Secure-maraberto_rt";

    /** Nome usado quando o Secure está desligado (dev em HTTP). */
    public static final String INSECURE_NAME = "maraberto_rt";

    public static final String PATH = "/api/v1/auth";

    private AuthCookie() {
    }

    public static String name(boolean secure) {
        return secure ? NAME : INSECURE_NAME;
    }

    public static ResponseCookie create(String value, Duration maxAge, boolean secure) {
        return ResponseCookie.from(name(secure), value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }

    public static ResponseCookie clear(boolean secure) {
        return ResponseCookie.from(name(secure), "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    /** Lê o refresh token do cookie, aceitando o nome seguro e o de dev. */
    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) || INSECURE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }
}
