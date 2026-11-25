package com.maraberto.identity.api;

import java.util.UUID;

/**
 * Dados do access token necessários para autorizar uma conexão ou requisição.
 *
 * <p>Nada de nome, e-mail ou CPF: o token carrega apenas o identificador, o
 * papel e a marca de senha temporária (ADR-009/ADR-027).
 */
public record AccessTokenClaims(UUID userId, String role, boolean mustChangePassword) {
}
