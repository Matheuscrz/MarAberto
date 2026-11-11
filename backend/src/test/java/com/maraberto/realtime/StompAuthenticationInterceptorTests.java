package com.maraberto.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maraberto.identity.api.AccessTokenClaims;
import com.maraberto.identity.api.AccessTokenVerifier;
import com.maraberto.realtime.infra.StompAuthenticationInterceptor;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/**
 * O handshake HTTP de {@code /ws} é aberto; a autorização acontece no frame
 * {@code CONNECT}. Sem essa checagem qualquer cliente assinaria a fila de SOS
 * de um operador pelo nome dele.
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
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer token-valido");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        StompHeaderAccessor resulting = StompHeaderAccessor.wrap(result);
        assertThat(resulting.getUser()).isNotNull();
        assertThat(resulting.getUser().getName()).isEqualTo(USER_ID.toString());
        assertThat(((org.springframework.security.core.Authentication) resulting.getUser())
                .getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_LIFEGUARD");
    }

    @Test
    void rejectsConnectWithoutToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void rejectsConnectWithInvalidToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer token-forjado");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void leavesOtherFramesUntouched() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/sos");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }
}
