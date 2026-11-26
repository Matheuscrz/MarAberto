package com.maraberto.identity.api.dto;

import java.util.List;
import java.util.UUID;

/** Escopo territorial (áreas de banho vinculadas) de um usuário. */
public record UserBeachScopesView(UUID userId, List<UUID> beachIds) {
}
