package com.maraberto.identity.api;

import java.util.UUID;

/**
 * Dados do access token necessários para autorizar uma conexão ou requisição.
 *
 * <p>Nada de nome, e-mail ou CPF: o token carrega apenas o identificador e o
 * papel (ADR-009).
 */
public record AccessTokenClaims(UUID userId, String role) {
}
