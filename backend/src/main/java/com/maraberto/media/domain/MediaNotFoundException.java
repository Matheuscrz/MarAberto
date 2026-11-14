package com.maraberto.media.domain;

import java.util.UUID;

/** Mídia inexistente ou removida do armazenamento. */
public class MediaNotFoundException extends RuntimeException {

    private final transient UUID mediaId;

    public MediaNotFoundException(UUID mediaId) {
        super("Mídia não encontrada.");
        this.mediaId = mediaId;
    }

    public UUID mediaId() {
        return mediaId;
    }
}
