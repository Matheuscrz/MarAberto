package com.maraberto.beaches.application;

/** KML inválido, incompleto ou com geometria que não pode ser importada. */
public class KmlParseException extends RuntimeException {

    public KmlParseException(String message) {
        super(message);
    }

    public KmlParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
