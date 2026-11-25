package com.maraberto.content.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Reordenação em lote dos conteúdos exibidos na Central Aprender.
 */
public record ReorderContentRequest(
        @NotEmpty(message = "Informe a lista ordenada de conteúdos")
        List<@NotNull UUID> orderedIds) {
}
