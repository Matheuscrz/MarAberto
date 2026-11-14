package com.maraberto.media.domain;

/** Arquivo acima do limite aceito para foto de sinalização. */
public class ImageTooLargeException extends RuntimeException {

    private final long maxBytes;

    public ImageTooLargeException(long maxBytes) {
        super("A foto deve ter no máximo " + (maxBytes / (1024 * 1024)) + " MB.");
        this.maxBytes = maxBytes;
    }

    public long maxBytes() {
        return maxBytes;
    }
}
