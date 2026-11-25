package com.maraberto.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Resultado da criação/reset: a senha temporária aparece **uma única vez**.
 *
 * <p>Não há e-mail no perfil mínimo (ADR-009); a TI entrega o valor
 * presencialmente ou por canal institucional. O servidor guarda só o hash.
 */
@Schema(name = "ProvisionedAdminUser",
        description = "Operador com a senha temporária exibida uma única vez")
public record ProvisionedAdminUserResponse(
        AdminUserView user,
        @Schema(description = "Senha temporária em claro; não será exibida de novo")
        String temporaryPassword,
        @Schema(description = "Fim da validade da senha temporária")
        Instant expiresAt) {
}
