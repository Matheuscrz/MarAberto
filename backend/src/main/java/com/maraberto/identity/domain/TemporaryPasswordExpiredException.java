package com.maraberto.identity.domain;

/** A senha temporária venceu antes de o operador definir a definitiva. */
public class TemporaryPasswordExpiredException extends RuntimeException {

    public TemporaryPasswordExpiredException() {
        super("A senha temporária expirou. Procure a TI para gerar outra.");
    }
}
