package com.maraberto.occurrences.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parâmetros configuráveis de detecção de duplicidade (RN13).
 */
@ConfigurationProperties(prefix = "maraberto.occurrences")
public record OccurrenceProperties(
        double duplicateRadiusMeters,
        Duration duplicateWindow) {

    public OccurrenceProperties {
        if (duplicateRadiusMeters <= 0) {
            duplicateRadiusMeters = 200.0;
        }
        if (duplicateWindow == null || duplicateWindow.isNegative() || duplicateWindow.isZero()) {
            duplicateWindow = Duration.ofMinutes(30);
        }
    }
}
