package com.maraberto.identity.infra.security;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proteção contra força bruta do login.
 *
 * <p>São duas camadas independentes: a conta carrega bloqueio persistido (vale
 * entre instâncias e reinícios) e o IP tem token bucket (contém o ataque que
 * varre muitos identificadores). O limite de IP fica sempre ativo, com Redis
 * distribuído e um balde local como piso quando o Redis cai.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "maraberto.security.login")
public class LoginProtectionProperties {

    /** Tentativas por janela para uma mesma origem. */
    private int ipCapacity = 30;

    /** Janela de recarga do balde por origem. */
    private Duration ipWindow = Duration.ofMinutes(5);

    /** Falhas consecutivas na mesma conta antes do primeiro bloqueio. */
    private int accountMaxAttempts = 5;

    /** Espera inicial ao atingir o limiar. */
    private Duration accountBaseLock = Duration.ofSeconds(30);

    /** Teto da espera progressiva. */
    private Duration accountMaxLock = Duration.ofMinutes(15);

    /** Desliga o limite de IP (só em teste). */
    private boolean ipEnabled = true;
}
