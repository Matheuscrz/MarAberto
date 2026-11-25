package com.maraberto.identity.domain;

/**
 * A TI não pode desativar nem retirar o papel {@code ADMIN} da própria conta.
 *
 * <p>Sem essa guarda, um clique deixaria a instalação sem administrador e sem
 * caminho de volta pela interface.
 */
public class SelfAdminProtectionException extends RuntimeException {

    public SelfAdminProtectionException() {
        super("A própria conta de TI não pode ser desativada nem perder o papel de administrador.");
    }
}
