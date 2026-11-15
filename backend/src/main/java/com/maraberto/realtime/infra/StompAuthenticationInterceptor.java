package com.maraberto.realtime.infra;

import com.maraberto.identity.api.AccessTokenClaims;
import com.maraberto.identity.api.AccessTokenVerifier;
import com.maraberto.realtime.api.RealtimeDestinations;
import com.maraberto.realtime.api.SosDestinations;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Autentica a sessão STOMP no frame {@code CONNECT} e autoriza cada
 * {@code SUBSCRIBE}.
 *
 * <p>O handshake HTTP em {@code /ws} continua aberto, mas sem JWT válido o
 * {@code CONNECT} é recusado. A partir daí a sessão tem um usuário, e o
 * {@code SUBSCRIBE} deixa de ser livre: só os destinos da allowlist são aceitos,
 * com o papel exigido por destino. Sem essa checagem qualquer cliente conectado
 * assinaria o tópico operacional e escutaria o SOS de todos os postos.
 *
 * <p>O broker em memória não tem autorização própria; toda a decisão está aqui,
 * no canal de entrada. Publicações do servidor para
 * {@code /user/{id}/queue/sos} passam pelo canal de saída e não são afetadas.
 */
@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Set<String> OPERATIONAL_ROLES =
            Set.of("LIFEGUARD", "COORDINATOR", "ADMIN");
    private static final Set<String> COORDINATION_ROLES = Set.of("COORDINATOR", "ADMIN");

    /** Prefixos de destino que o cliente nunca pode publicar diretamente. */
    private static final List<String> BROKER_PREFIXES = List.of("/topic", "/queue", "/user");

    private final AccessTokenVerifier tokenVerifier;

    public StompAuthenticationInterceptor(AccessTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> denyBrokerPublish(accessor);
            default -> {
                // UNSUBSCRIBE, ACK, NACK, DISCONNECT: nada a autorizar aqui.
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        AccessTokenClaims claims = tokenVerifier.verify(extractToken(accessor))
                .orElseThrow(() -> new MessagingException(
                        "Access token ausente ou inválido para a conexão STOMP"));

        accessor.setUser(new UsernamePasswordAuthenticationToken(
                claims.userId().toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + claims.role()))));
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Authentication authentication = userOf(accessor);
        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw new MessagingException("Assinatura STOMP sem destino");
        }

        if (SosDestinations.SOS_SUBSCRIPTION.equals(destination)) {
            requireAnyRole(authentication, OPERATIONAL_ROLES, destination);
            return;
        }
        if (RealtimeDestinations.OPERATIONAL_EMERGENCY_CALLS.equals(destination)) {
            requireAnyRole(authentication, COORDINATION_ROLES, destination);
            return;
        }
        throw new MessagingException("Destino STOMP não autorizado: " + destination);
    }

    private void denyBrokerPublish(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        boolean brokerDestination = BROKER_PREFIXES.stream().anyMatch(destination::startsWith);
        if (brokerDestination) {
            throw new MessagingException("Publicação direta no broker não é permitida: " + destination);
        }
    }

    private static Authentication userOf(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication authentication) {
            return authentication;
        }
        throw new MessagingException("Sessão STOMP sem usuário autenticado");
    }

    private static void requireAnyRole(
            Authentication authentication,
            Set<String> roles,
            String destination) {
        boolean authorized = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> roles.contains(stripPrefix(authority)));
        if (!authorized) {
            throw new MessagingException("Papel sem acesso ao destino STOMP: " + destination);
        }
    }

    private static String stripPrefix(String authority) {
        return authority.startsWith("ROLE_") ? authority.substring("ROLE_".length()) : authority;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return accessor.getFirstNativeHeader("access_token");
    }
}
