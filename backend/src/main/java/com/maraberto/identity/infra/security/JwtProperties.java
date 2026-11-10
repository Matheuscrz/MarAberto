package com.maraberto.identity.infra.security;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parâmetros de emissão e rotação de tokens (ADR-009).
 *
 * <p>O access token é um JWT curto que o frontend guarda só em memória. O
 * refresh é opaco, dura mais e viaja no cookie {@code __Secure-maraberto_rt}.
 * A janela de tolerância cobre o caso de duas abas renovando ao mesmo tempo:
 * dentro dela a rotação é considerada benigna; fora, o token é tratado como
 * reutilizado e a família inteira é revogada.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "maraberto.security.jwt")
public class JwtProperties {

    /** Segredo HMAC-SHA256 com no mínimo 32 bytes. */
    private String secret;

    private String issuer = "maraberto";

    private Duration accessTokenTtl = Duration.ofMinutes(15);

    private Duration refreshTokenTtl = Duration.ofDays(7);

    private Duration refreshReuseTolerance = Duration.ofSeconds(10);
}
