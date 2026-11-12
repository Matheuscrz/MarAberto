package com.maraberto.occupancy.application;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parâmetros do indicador de ocupação (RF10/RN06).
 *
 * <p>A janela é móvel e curta de propósito: presença velha não descreve a praia
 * agora e qualquer retenção maior viraria histórico.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "maraberto.occupancy")
public class OccupancyProperties {

    /** Janela móvel do indicador. */
    private Duration window = Duration.ofMinutes(15);

    /** Amostra mínima para sair de "dados insuficientes" (RN06). */
    private int minimumSample = 5;

    /** A partir daqui a faixa é MODERATE. */
    private int moderateThreshold = 10;

    /** A partir daqui a faixa é HIGH. */
    private int highThreshold = 25;

    /** A partir daqui a faixa é EXTREME. */
    private int extremeThreshold = 50;

    /** Tempo máximo de retenção das amostras; o expurgo roda na escrita. */
    private Duration retention = Duration.ofHours(24);
}
