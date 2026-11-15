package com.maraberto.identity.domain;

/**
 * Origem não permitida em uma rota de sessão.
 *
 * <p>Só acontece quando a requisição traz {@code Origin} e ele não está na
 * allowlist. Sem {@code Origin} (apps nativos, clientes de linha de comando) a
 * requisição segue: o cookie {@code SameSite=Strict} continua sendo a defesa.
 */
public class OriginNotAllowedException extends RuntimeException {

    public OriginNotAllowedException() {
        super("Origem não permitida para esta operação de sessão");
    }
}
