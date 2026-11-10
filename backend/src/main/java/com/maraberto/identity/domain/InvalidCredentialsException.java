package com.maraberto.identity.domain;

/**
 * Credenciais inválidas (identificador inexistente ou senha incorreta).
 *
 * <p>A mesma exceção é usada nos dois casos para não revelar quais matrículas
 * existem. O handler traduz para HTTP 401 com RFC 9457.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciais inválidas");
    }
}
