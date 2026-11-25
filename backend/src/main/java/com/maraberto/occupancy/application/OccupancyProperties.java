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

    /** Espaço médio ocupado por uma pessoa na área de banho (em m²). */
    private double spacePerPersonSqm = 10.0;

    /** Fração da capacidade estimada a partir da qual a faixa é MODERATE. */
    private double moderateRatio = 0.25;

    /** Fração da capacidade estimada a partir da qual a faixa é HIGH. */
    private double highRatio = 0.50;

    /** Fração da capacidade estimada a partir da qual a faixa é EXTREME. */
    private double extremeRatio = 0.80;

    /** A partir daqui a faixa é MODERATE quando não há área válida (fallback). */
    private int moderateThreshold = 10;

    /** A partir daqui a faixa é HIGH quando não há área válida (fallback). */
    private int highThreshold = 25;

    /** A partir daqui a faixa é EXTREME quando não há área válida (fallback). */
    private int extremeThreshold = 50;

    /** Tempo máximo de retenção das amostras; o expurgo roda na escrita. */
    private Duration retention = Duration.ofHours(24);
}
