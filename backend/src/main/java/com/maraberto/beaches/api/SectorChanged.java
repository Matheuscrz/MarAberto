package com.maraberto.beaches.api;

import java.util.UUID;

/** Setor criado, atualizado ou desativado por uma importação. */
public record SectorChanged(UUID beachId, String externalId) {
}
