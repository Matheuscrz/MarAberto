package com.maraberto.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maraberto.identity.api.AccessTokenClaims;
import com.maraberto.identity.api.AccessTokenVerifier;
import com.maraberto.realtime.api.RealtimeDestinations;
import com.maraberto.realtime.api.SosDestinations;
import com.maraberto.realtime.infra.StompAuthenticationInterceptor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * O handshake HTTP de {@code /ws} é aberto; a autenticação acontece no frame
 * {@code CONNECT} e a autorização de destino no {@code SUBSCRIBE}. Sem essa
 * checagem qualquer cliente assinaria o tópico operacional e escutaria o SOS de
 * todos os postos — o broker em memória não aplica papel por conta própria.
 */
class StompAuthenticationInterceptorTests {

    private static final UUID USER_ID = UUID.randomUUID();

    private final AccessTokenVerifier verifier = token -> "token-valido".equals(token)
            ? Optional.of(new AccessTokenClaims(USER_ID, "LIFEGUARD"))
            : Optional.empty();

    private final StompAuthenticationInterceptor interceptor =
            new StompAuthenticationInterceptor(verifier);

    @Test
    void authenticatesConnectFrameWithBearerToken() {
        Message<?> result = interceptor.preSend(connect("Bearer token-valido"), null);

        StompHeaderAccessor resulting = StompHeaderAccessor.wrap(result);
        assertThat(resulting.getUser()).isNotNull();
        assertThat(resulting.getUser().getName()).isEqualTo(USER_ID.toString());
        assertThat(((Authentication) resulting.getUser()).getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_LIFEGUARD");
    }

    @Test
    void rejectsConnectWithoutToken() {
        assertThatThrownBy(() -> interceptor.preSend(connect(null), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void rejectsConnectWithInvalidToken() {
        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer token-forjado"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void lifeguardCanSubscribeToOwnSosQueue() {
        Message<?> result = interceptor.preSend(
                subscribe(SosDestinations.SOS_SUBSCRIPTION, "LIFEGUARD"), null);

        assertThat(result).isNotNull();
    }

    @Test
    void coordinatorCanSubscribeToOperationalTopic() {
        Message<?> result = interceptor.preSend(
                subscribe(RealtimeDestinations.OPERATIONAL_EMERGENCY_CALLS, "COORDINATOR"), null);

        assertThat(result).isNotNull();
    }

    @Test
    void lifeguardCannotSubscribeToOperationalTopic() {
        assertThatThrownBy(() -> interceptor.preSend(
                subscribe(RealtimeDestinations.OPERATIONAL_EMERGENCY_CALLS, "LIFEGUARD"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void rejectsAnyOtherSubscriptionDestination() {
        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/anything-else", "ADMIN"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void rejectsSubscriptionWithoutAuthenticatedSession() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(SosDestinations.SOS_SUBSCRIPTION);
        accessor.setLeaveMutable(true);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void rejectsDirectPublishToBrokerDestination() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/operational.emergency.calls");
        accessor.setUser(authentication("COORDINATOR"));
        accessor.setLeaveMutable(true);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void leavesNonSubscriptionFramesUntouched() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        accessor.setDestination("/topic/qualquer");
        accessor.setLeaveMutable(true);
        Message<byte[]> frame = message(accessor);

        assertThat(interceptor.preSend(frame, null)).isSameAs(frame);
    }

    private Message<?> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        accessor.setLeaveMutable(true);
        return message(accessor);
    }

    private Message<?> subscribe(String destination, String role) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(authentication(role));
        accessor.setLeaveMutable(true);
        return message(accessor);
    }

    private static Authentication authentication(String role) {
        return new UsernamePasswordAuthenticationToken(
                USER_ID.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
