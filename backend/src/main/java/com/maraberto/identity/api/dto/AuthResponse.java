package com.maraberto.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resposta de login e refresh.
 *
 * <p>O access token fica só em memória no cliente; o refresh opaco vai no cookie
 * {@code __Secure-maraberto_rt} e nunca aparece no corpo.
 */
@Schema(name = "AuthResponse", description = "Sessão emitida: access token em memória + operador")
public record AuthResponse(
        @Schema(description = "JWT de acesso, válido por `expiresIn` segundos")
        String accessToken,
        @Schema(description = "Esquema do token", example = "Bearer")
        String tokenType,
        @Schema(description = "Validade do access token em segundos", example = "900")
        long expiresIn,
        @Schema(description = "Operador autenticado")
        AuthenticatedUserResponse user) {
}
