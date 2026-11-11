package com.maraberto.realtime.api;

import java.util.UUID;

/**
 * Porta de notificação individual do SOS.
 *
 * <p>A lista de destinatários já foi resolvida pelo banco (ADR-018). Esta porta
 * apenas entrega o alerta ao operador certo, em destino autenticado; ela nunca
 * decide quem deve receber.
 */
public interface SosNotifierPort {

    /**
     * Entrega um payload ao operador informado, na fila pessoal de SOS.
     *
     * @param userId  operador destinatário (resolvido na transação do chamado)
     * @param payload objeto serializável, sem coordenadas nem dados pessoais
     */
    void send(UUID userId, Object payload);
}
