package com.maraberto.content.api;

import java.util.UUID;

/**
 * Evento publicado quando o catálogo educativo/de divulgação muda.
 *
 * <p>Consome-se em {@code AFTER_COMMIT} para invalidar o cache da leitura pública
 * ({@code GET /api/v1/content}) sem risco de repovoar com estado anterior.
 */
public record ContentChanged(UUID contentId, String action) {
}
