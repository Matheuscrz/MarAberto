package com.maraberto.identity.api.dto;

import com.maraberto.identity.domain.OperationalUser;
import com.maraberto.identity.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Dados públicos do operador autenticado.
 *
 * <p>É a resposta de {@code GET /api/v1/auth/me} e o objeto {@code user} do
 * login/refresh. Nunca inclui senha, hash, CPF nem token.
 */
@Schema(name = "AuthenticatedUser", description = "Operador autenticado, sem dados sensíveis")
public record AuthenticatedUserResponse(
        @Schema(description = "Identificador do operador")
        UUID id,
        @Schema(description = "Nome de exibição", example = "Sgt. Silva")
        String name,
        @Schema(description = "Perfil de acesso", example = "LIFEGUARD")
        Role role,
        @Schema(description = "Matrícula funcional, quando houver", example = "BM-10492")
        String badgeNumber,
        @Schema(description = "E-mail do próprio operador", example = "operador@maraberto.dev")
        String email) {

    public static AuthenticatedUserResponse from(OperationalUser user) {
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getName(),
                user.getRole(),
                user.getBadgeNumber(),
                user.getEmail());
    }
}
