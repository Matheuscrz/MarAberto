package com.maraberto.realtime.infra;

import com.maraberto.identity.api.AccessTokenClaims;
import com.maraberto.identity.api.AccessTokenVerifier;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Autentica a sessão STOMP no frame {@code CONNECT}.
 *
 * <p>O handshake HTTP em {@code /ws} continua aberto, mas sem JWT válido o
 * {@code CONNECT} é recusado — e sem sessão autenticada não existe assinatura em
 * {@code /user/queue/sos}. É o que impede que qualquer cliente escute o SOS de
 * todos os postos.
 */
@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenVerifier tokenVerifier;

    public StompAuthenticationInterceptor(AccessTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        AccessTokenClaims claims = tokenVerifier.verify(extractToken(accessor))
                .orElseThrow(() -> new MessagingException(
                        "Access token ausente ou inválido para a conexão STOMP"));

        accessor.setUser(new UsernamePasswordAuthenticationToken(
                claims.userId().toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + claims.role()))));
        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return accessor.getFirstNativeHeader("access_token");
    }
}
