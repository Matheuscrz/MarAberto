package com.maraberto.beaches.application;

import java.util.UUID;

/** Área de banho inexistente ou inativa no mapa público. */
public class BeachNotFoundException extends RuntimeException {

    public BeachNotFoundException(UUID beachId) {
        super("Área de banho " + beachId + " não encontrada");
    }
}
