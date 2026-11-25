package com.maraberto.identity.api.dto;

import com.maraberto.shared.security.sanitization.Sanitized;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Troca da senha temporária pela definitiva.
 *
 * <p>A senha atual não é sanitizada (precisa chegar intacta ao Argon2id); a nova
 * é validada pela política do servidor.
 */
@Schema(name = "ChangePasswordRequest", description = "Troca obrigatória da senha temporária")
public record ChangePasswordRequest(
        @Schema(description = "Senha atual (temporária)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 128)
        String currentPassword,
        @Schema(description = "Nova senha definitiva", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(min = 12, max = 128)
        String newPassword) {
}
