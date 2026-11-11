package com.maraberto.emergency.domain;

/**
 * O operador autenticado não é um dos destinatários do chamado.
 *
 * <p>Serve também para não revelar a existência de um SOS de outra área: a
 * resposta é a mesma de um chamado inexistente.
 */
public class DispatchNotAssignedException extends RuntimeException {

    public DispatchNotAssignedException() {
        super("O chamado não está atribuído a este operador");
    }
}
