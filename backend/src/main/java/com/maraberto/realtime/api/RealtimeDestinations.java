package com.maraberto.realtime.api;

/**
 * Destinos STOMP estáveis do MarAberto.
 *
 * <p>Os banhistas <strong>não</strong> assinam WebSocket: a leitura pública
 * continua em HTTP com cache e {@code ETag} (ADR-008). Estes destinos são
 * consumidos pelo console operacional.
 */
public final class RealtimeDestinations {

    /**
     * Fila de SOS abertos para o console operacional.
     *
     * <p>O payload é <strong>redigido</strong>: identificador do chamado,
     * estado e horário. Coordenadas e {@code clientRequestId} não trafegam
     * aqui — o console busca o detalhe por uma rota autenticada quando a
     * etapa de identidade existir.
     */
    public static final String OPERATIONAL_EMERGENCY_CALLS = "/topic/operational.emergency.calls";

    private RealtimeDestinations() {
    }
}
