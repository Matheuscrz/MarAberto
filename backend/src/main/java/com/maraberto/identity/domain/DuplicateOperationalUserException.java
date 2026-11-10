package com.maraberto.identity.domain;

/**
 * Cadastro duplicado de operador (CPF, e-mail ou matrícula já usados).
 */
public class DuplicateOperationalUserException extends RuntimeException {

    public DuplicateOperationalUserException(String message) {
        super(message);
    }
}
