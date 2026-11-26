package com.maraberto.identity.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.maraberto.identity.api.AccessTokenClaims;
import com.maraberto.identity.domain.OperationalUser;
import com.maraberto.identity.domain.Role;
import com.maraberto.identity.infra.OperationalUserRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Valida que o verificador usado no STOMP aplica revogação e revalidação de active/role (F-02). */
class JwtAccessTokenVerifierTests {

    private JwtService jwtService;
    private TokenRevocationService revocationService;
    private OperationalUserRepository userRepository;
    private JwtAccessTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-for-stomp-verifier-32-bytes-minimum!");
        properties.setIssuer("maraberto");
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        jwtService = new JwtService(properties, new MockEnvironment());
        revocationService = mock(TokenRevocationService.class);
        userRepository = mock(OperationalUserRepository.class);
        verifier = new JwtAccessTokenVerifier(jwtService, revocationService, userRepository);
    }

    @Test
    void acceptsValidNonRevokedActiveUserToken() {
        OperationalUser user = user(Role.LIFEGUARD);
        String token = jwtService.generateAccessToken(user);
        when(revocationService.isRevoked(any(), eq(user.getId()), any())).thenReturn(false);
        when(userRepository.findActiveRoleById(user.getId())).thenReturn(Optional.of(Role.LIFEGUARD));

        Optional<AccessTokenClaims> result = verifier.verify(token);

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo(user.getId());
        assertThat(result.get().role()).isEqualTo("LIFEGUARD");
        assertThat(result.get().mustChangePassword()).isFalse();
    }

    @Test
    void rejectsRevokedToken() {
        OperationalUser user = user(Role.LIFEGUARD);
        String token = jwtService.generateAccessToken(user);
        when(revocationService.isRevoked(any(), eq(user.getId()), any())).thenReturn(true);

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    void rejectsInactiveUserOrChangedRole() {
        OperationalUser user = user(Role.COORDINATOR);
        String token = jwtService.generateAccessToken(user);
        when(revocationService.isRevoked(any(), eq(user.getId()), any())).thenReturn(false);

        when(userRepository.findActiveRoleById(user.getId())).thenReturn(Optional.empty());
        assertThat(verifier.verify(token)).isEmpty();

        when(userRepository.findActiveRoleById(user.getId())).thenReturn(Optional.of(Role.LIFEGUARD));
        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    void rejectsMfaChallengeToken() {
        UUID userId = UUID.randomUUID();
        String challengeToken = jwtService.generateMfaChallengeToken(userId);

        assertThat(verifier.verify(challengeToken)).isEmpty();
    }

    private static OperationalUser user(Role role) {
        return new OperationalUser(
                UUID.randomUUID(),
                "Operador Teste",
                "BM-1000",
                "operador@teste.dev",
                "email-bidx",
                "12345678909",
                "cpf-bidx",
                "hash",
                role);
    }
}
