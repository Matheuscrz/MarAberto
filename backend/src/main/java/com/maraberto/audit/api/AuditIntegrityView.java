package com.maraberto.audit.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado da conferência do encadeamento {@code prev_hash}/{@code hash}.
 *
 * @param checked        linhas verificadas
 * @param valid          {@code true} quando toda a cadeia confere
 * @param firstInvalidId primeira linha divergente, quando houver
 */
@Schema(name = "AuditIntegrity", description = "Conferência da cadeia de hashes da trilha")
public record AuditIntegrityView(long checked, boolean valid, Long firstInvalidId) {
}
