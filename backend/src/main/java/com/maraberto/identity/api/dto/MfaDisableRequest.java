package com.maraberto.identity.api.dto;

import com.maraberto.shared.security.sanitization.Sanitized;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MfaDisableRequest(
        @NotBlank(message = "Informe a senha atual")
        @Size(max = 200)
        String currentPassword,
        @Sanitized
        @NotBlank(message = "Informe o código do autenticador ou um código de recuperação")
        @Size(max = 32)
        String code) {
}
