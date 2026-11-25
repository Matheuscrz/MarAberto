package com.maraberto.content.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Visão de um item educativo ou material de divulgação.
 */
public record ContentView(
        UUID id,
        ContentKind kind,
        String title,
        String description,
        String category,
        List<String> tags,
        String externalUrl,
        String externalLinkLabel,
        String youtubeId,
        String duration,
        int displayOrder,
        boolean published,
        Instant createdAt,
        Instant updatedAt) {
}
