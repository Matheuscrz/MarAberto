package com.maraberto.identity.domain;

/**
 * Excesso de tentativas de login a partir da mesma origem.
 *
 * <p>É um limite por IP, não por conta: não revela se o identificador existe.
 * A resposta é 429 com {@code Retry-After}.
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("Muitas tentativas de login");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
