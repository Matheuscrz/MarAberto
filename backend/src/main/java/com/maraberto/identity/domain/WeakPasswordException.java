package com.maraberto.identity.domain;

/** A nova senha não atende à política mínima. */
public class WeakPasswordException extends RuntimeException {

    public WeakPasswordException(String message) {
        super(message);
    }
}
