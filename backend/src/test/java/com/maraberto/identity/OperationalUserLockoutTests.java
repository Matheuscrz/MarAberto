package com.maraberto.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.maraberto.identity.domain.OperationalUser;
import com.maraberto.identity.domain.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bloqueio progressivo da conta: cada falha depois do limiar dobra a espera até
 * o teto. O teste isola a regra do banco.
 */
class OperationalUserLockoutTests {

    private static final Duration BASE = Duration.ofSeconds(30);
    private static final Duration MAX = Duration.ofMinutes(15);

    private final OperationalUser user = new OperationalUser(
            UUID.randomUUID(),
            "Operador Teste",
            "BM-1",
            "operador@maraberto.dev",
            "email-bidx",
            "11144477735",
            "cpf-bidx",
            "$argon2id$hash",
            Role.LIFEGUARD);

    @Test
    void belowTheThresholdDoesNotLock() {
        Instant now = Instant.parse("2026-11-15T12:00:00Z");

        Instant lock = user.registerFailedLogin(now, 5, BASE, MAX);

        assertThat(lock).isEqualTo(now);
        assertThat(user.isLockedAt(now)).isFalse();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void lockGrowsExponentiallyAndStopsAtTheCeiling() {
        Instant now = Instant.parse("2026-11-15T12:00:00Z");

        for (int attempt = 1; attempt <= 4; attempt += 1) {
            user.registerFailedLogin(now, 5, BASE, MAX);
        }

        assertThat(user.registerFailedLogin(now, 5, BASE, MAX))
                .isEqualTo(now.plusSeconds(30));
        assertThat(user.registerFailedLogin(now, 5, BASE, MAX))
                .isEqualTo(now.plusSeconds(60));
        assertThat(user.registerFailedLogin(now, 5, BASE, MAX))
                .isEqualTo(now.plusSeconds(120));

        for (int attempt = 0; attempt < 20; attempt += 1) {
            user.registerFailedLogin(now, 5, BASE, MAX);
        }
        assertThat(user.getLockedUntil()).isEqualTo(now.plus(MAX));
    }

    @Test
    void successfulLoginClearsTheState() {
        Instant now = Instant.parse("2026-11-15T12:00:00Z");
        for (int attempt = 0; attempt < 5; attempt += 1) {
            user.registerFailedLogin(now, 5, BASE, MAX);
        }

        user.resetFailedLogins();

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.isLockedAt(now)).isFalse();
    }
}
