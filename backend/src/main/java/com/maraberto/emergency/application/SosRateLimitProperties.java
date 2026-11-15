package com.maraberto.emergency.application;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parâmetros do limite de chamados do SOS anônimo (RN05).
 *
 * <p>O limite existe contra abuso, nunca contra a vítima: o token bucket
 * devolve a ficha aos poucos, então quem precisa de um quarto chamado em
 * sequência espera segundos, não a janela inteira. Desligar o limite só é
 * aceitável em teste.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "maraberto.emergency.rate-limit")
public class SosRateLimitProperties {

    /** Liga o limite; em teste pode ser desligado para não interferir. */
    private boolean enabled = true;

    /** Chamados permitidos por janela (RN05: três por dez minutos). */
    private int capacity = 3;

    /** Janela de recarga das fichas. */
    private Duration window = Duration.ofMinutes(10);
}
