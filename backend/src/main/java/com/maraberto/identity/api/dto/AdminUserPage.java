package com.maraberto.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Página de operadores da administração, ordenada do cadastro mais recente para
 * o mais antigo.
 */
@Schema(name = "AdminUserPage", description = "Página de operadores da TI")
public record AdminUserPage(
        List<AdminUserView> items,
        @Schema(description = "Cursor opaco da próxima página; nulo na última")
        String nextCursor,
        boolean hasMore,
        @Schema(description = "Total que atende ao filtro, independente da página")
        long total) {
}
