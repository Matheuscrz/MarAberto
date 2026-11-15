package com.maraberto.emergency.domain;

/**
 * Limite de chamados do SOS excedido (RN05).
 *
 * <p>Carrega apenas o tempo de espera; o IP que originou o bloqueio nunca sai
 * do limitador — nem para a resposta, nem para o log.
 */
public class SosRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public SosRateLimitedException(long retryAfterSeconds) {
        super("Limite de chamados de emergência excedido");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
