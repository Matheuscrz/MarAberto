package com.maraberto.identity.domain;

import java.util.UUID;

/** Operador inexistente na administração da TI. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("Operador não encontrado: " + userId);
    }
}
