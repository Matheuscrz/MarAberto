package com.maraberto.shared.redis;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Circuit breaker leve (sem dependências externas) para operações opcionais no
 * Redis (blacklist JWT e rate limit distribuído).
 *
 * <p>Quando o Redis falha, abre o circuito pela janela {@code cooldown} para
 * que o restante das requisições caia imediatamente no fallback local sem pagar
 * o timeout de rede em cada chamada. Ao vencer a janela, apenas uma thread
 * sonda a reconexão (half-open via CAS), evitando tempestade de timeouts sob
 * carga alta.
 */
@Component
public class RedisAvailabilityGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisAvailabilityGuard.class);

    private final long cooldownMillis;
    private final Clock clock;
    private final AtomicLong nextProbeAtMillis = new AtomicLong(0L);

    @Autowired
    public RedisAvailabilityGuard(
            @Value("${maraberto.redis.circuit-breaker.cooldown:PT15S}") Duration cooldown) {
        this(cooldown, Clock.systemUTC());
    }

    public RedisAvailabilityGuard(Duration cooldown, Clock clock) {
        this.cooldownMillis = cooldown == null || cooldown.isNegative()
                ? 0L
                : cooldown.toMillis();
        this.clock = clock;
    }

    /**
     * Indica se a chamada atual deve tentar falar com o Redis.
     *
     * <p>No caminho saudável (circuito fechado), executa apenas uma leitura
     * volátil sem contenção. Com circuito aberto, bloqueia tentativas até o fim
     * do cooldown e libera exatamente uma sonda por janela.
     */
    public boolean allowAttempt() {
        if (cooldownMillis <= 0L) {
            return true;
        }
        long openUntil = nextProbeAtMillis.get();
        if (openUntil == 0L) {
            return true;
        }
        long now = clock.millis();
        if (now < openUntil) {
            return false;
        }
        return nextProbeAtMillis.compareAndSet(openUntil, now + cooldownMillis);
    }

    /** Fecha o circuito após uma operação bem-sucedida no Redis. */
    public void recordSuccess() {
        if (nextProbeAtMillis.get() != 0L && nextProbeAtMillis.getAndSet(0L) != 0L) {
            log.info("Redis recuperado; circuito fechado novamente");
        }
    }

    /** Abre (ou estende) o circuito após falha de comunicação/timeout com o Redis. */
    public void recordFailure(RuntimeException exception) {
        if (cooldownMillis <= 0L) {
            return;
        }
        long now = clock.millis();
        long previous = nextProbeAtMillis.getAndSet(now + cooldownMillis);
        if (previous == 0L) {
            log.warn("Redis indisponível ({}); ativando fallback local por {} ms",
                    exception.getClass().getSimpleName(), cooldownMillis);
        }
    }

    public boolean isOpen() {
        long openUntil = nextProbeAtMillis.get();
        return openUntil != 0L && clock.millis() < openUntil;
    }
}
