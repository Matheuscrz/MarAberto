package com.maraberto.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "MfaConfirmResponse", description = "Códigos de recuperação de uso único (exibidos uma única vez)")
public record MfaConfirmResponse(
        @Schema(description = "Códigos de recuperação gerados; guarde em local seguro")
        List<String> recoveryCodes) {
}
