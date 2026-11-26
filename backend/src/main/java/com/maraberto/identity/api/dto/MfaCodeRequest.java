package com.maraberto.identity.api.dto;

import com.maraberto.shared.security.sanitization.Sanitized;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MfaCodeRequest(
        @Sanitized
        @NotBlank(message = "Informe o código do aplicativo autenticador")
        @Size(max = 32)
        String code) {
}
