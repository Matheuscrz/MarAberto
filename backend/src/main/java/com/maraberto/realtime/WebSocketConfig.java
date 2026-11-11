package com.maraberto.realtime;

import com.maraberto.realtime.infra.StompAuthenticationInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuração de WebSocket/STOMP para o console operacional.
 *
 * <p>O público banhista continua em polling com ETag; apenas os operacionais
 * mantêm conexão. O frame {@code CONNECT} exige JWT válido e a entrega é feita
 * em destino por usuário ({@code /user/{userId}/queue/sos}), com os
 * destinatários resolvidos pelo banco (ADR-018). O fan-out entre instâncias via
 * Redis Pub/Sub entra na etapa de tempo real.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOrigins;
    private final StompAuthenticationInterceptor authenticationInterceptor;

    public WebSocketConfig(
            @Value("${maraberto.realtime.allowed-origins:http://localhost:5173,http://localhost:3000}")
                    String[] allowedOrigins,
            StompAuthenticationInterceptor authenticationInterceptor) {
        this.allowedOrigins = allowedOrigins;
        this.authenticationInterceptor = authenticationInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authenticationInterceptor);
    }
}
