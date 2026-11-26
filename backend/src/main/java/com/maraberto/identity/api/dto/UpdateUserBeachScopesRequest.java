package com.maraberto.identity.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Atualização do escopo territorial de um usuário pela TI (F-06).
 * Lista vazia remove a restrição (escopo irrestrito).
 */
public record UpdateUserBeachScopesRequest(
        @NotNull @Size(max = 500) List<@NotNull UUID> beachIds) {
}
