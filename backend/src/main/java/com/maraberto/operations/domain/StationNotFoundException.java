package com.maraberto.operations.domain;

/** Posto ou base móvel inexistente. */
public class StationNotFoundException extends RuntimeException {

    public StationNotFoundException() {
        super("Posto ou base móvel não encontrado.");
    }
}
