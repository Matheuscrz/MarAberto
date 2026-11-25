package com.maraberto.identity.api.dto;

import com.maraberto.identity.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Alteração de papel e/ou estado da conta.
 *
 * <p>Campos omitidos permanecem como estão. Não existe exclusão: desativar
 * preserva plantões, ocorrências e a própria auditoria.
 */
@Schema(name = "UpdateAdminUserRequest",
        description = "Altera papel e/ou ativação; omitir um campo mantém o valor atual")
public record UpdateAdminUserRequest(
        @Schema(description = "Novo perfil de acesso", example = "COORDINATOR")
        Role role,
        @Schema(description = "Conta ativa; `false` revoga as sessões e impede login",
                example = "false")
        Boolean active) {
}
