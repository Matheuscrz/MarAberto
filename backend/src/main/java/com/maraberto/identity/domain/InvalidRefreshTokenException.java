package com.maraberto.identity.domain;

/**
 * Token de refresh ausente, expirado, revogado ou reutilizado.
 *
 * <p>A reutilização de um token já rotacionado é tratada como indício de roubo:
 * a família inteira é revogada antes de lançar esta exceção.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Sessão inválida ou expirada");
    }
}
