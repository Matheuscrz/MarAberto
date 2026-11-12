package com.maraberto.beaches.application;

/**
 * Consulta pública sem posição utilizável (ausente, fora do planeta ou raio
 * acima do teto de 30 km).
 */
public class InvalidLocationException extends RuntimeException {

    public InvalidLocationException(String message) {
        super(message);
    }
}
