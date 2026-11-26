package com.maraberto.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Dispositivo / família de sessão de refresh ativa do operador autenticado.
 * Não expõe IP em claro nem o hash do token.
 */
public record ActiveSessionView(
        UUID id,
        String userAgent,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        boolean current) {
}
