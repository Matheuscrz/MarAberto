package com.maraberto.realtime.api;

/**
 * Porta de publicação de mensagens em tempo real.
 *
 * <p>Outros módulos publicam eventos de domínio por esta porta sem conhecer o
 * transporte (hoje STOMP/WebSocket; amanhã também Web Push e fan-out via
 * Redis). A entrega é <em>at-most-once</em>: quem precisa de garantia usa a
 * própria outbox, como o módulo {@code emergency}.
 *
 * <p>Nunca publique dados pessoais em destinos abertos ({@code /topic/**}):
 * a autorização de WebSocket por papel só entra na etapa de identidade.
 */
public interface RealtimePublisherPort {

    /**
     * Publica um payload em um destino STOMP.
     *
     * @param destination destino no formato {@code /topic/...}
     * @param payload objeto serializável em JSON, já sanitizado e sem PII
     */
    void publish(String destination, Object payload);
}
