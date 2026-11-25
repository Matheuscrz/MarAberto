package com.maraberto.content.domain;

import java.util.UUID;

/**
 * Conteúdo educativo não localizado pelo identificador informado.
 */
public class ContentNotFoundException extends RuntimeException {

    public ContentNotFoundException(UUID id) {
        super("Conteúdo não encontrado: " + id);
    }
}
