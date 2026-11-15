package com.maraberto.shared.ratelimit;

/**
 * Resultado de uma tentativa de consumo em um limitador.
 *
 * <p>{@code retryAfterSeconds} só tem significado quando a tentativa foi
 * negada: é o tempo mínimo para o balde voltar a ter uma ficha. O valor nunca é
 * menor que 1 para o cabeçalho {@code Retry-After} ser válido.
 */
public record RateLimitResult(boolean allowed, long retryAfterSeconds) {

    public static RateLimitResult permit() {
        return new RateLimitResult(true, 0);
    }

    public static RateLimitResult reject(long retryAfterSeconds) {
        return new RateLimitResult(false, Math.max(1, retryAfterSeconds));
    }
}
