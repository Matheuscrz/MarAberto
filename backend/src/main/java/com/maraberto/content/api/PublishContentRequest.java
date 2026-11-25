package com.maraberto.content.api;

import jakarta.validation.constraints.NotNull;

/**
 * Publicação ou despublicação rápida de um conteúdo pela coordenação.
 */
public record PublishContentRequest(
        @NotNull(message = "Informe o estado de publicação")
        Boolean published) {
}
