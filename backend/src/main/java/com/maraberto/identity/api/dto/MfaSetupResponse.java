package com.maraberto.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MfaSetupResponse", description = "Segredo TOTP pendente de confirmação")
public record MfaSetupResponse(
        @Schema(description = "Segredo Base32 para entrada manual no aplicativo autenticador")
        String secret,
        @Schema(description = "URI otpauth://totp/... para leitura por QR Code")
        String otpauthUri) {
}
