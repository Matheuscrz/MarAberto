package com.maraberto.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.maraberto.identity.domain.OperationalUser;
import com.maraberto.identity.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Operador na administração da TI.
 *
 * <p>Devolve e-mail e matrícula para identificação, mas nunca CPF, hash de senha
 * ou token. {@code lastAccessAt} vem da última sessão de refresh.
 */
@Schema(name = "AdminUser", description = "Operador administrado pela TI")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminUserView(
        UUID id,
        String name,
        String email,
        String badgeNumber,
        Role role,
        boolean active,
        boolean mustChangePassword,
        Instant temporaryPasswordExpiresAt,
        Instant passwordChangedAt,
        Instant createdAt,
        Instant lastAccessAt) {

    public static AdminUserView from(OperationalUser user, Instant lastAccessAt) {
        return new AdminUserView(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getBadgeNumber(),
                user.getRole(),
                user.isActive(),
                user.isMustChangePassword(),
                user.getTemporaryPasswordExpiresAt(),
                user.getPasswordChangedAt(),
                user.getCreatedAt(),
                lastAccessAt);
    }
}
