package com.maraberto.identity.domain;

/**
 * Operador autenticado corretamente, mas desativado pela coordenação/TI.
 *
 * <p>Só é lançada depois de validar a senha: quem não sabe a senha continua
 * recebendo {@link InvalidCredentialsException} (401).
 */
public class AccountInactiveException extends RuntimeException {

    public AccountInactiveException() {
        super("Conta inativa");
    }
}
