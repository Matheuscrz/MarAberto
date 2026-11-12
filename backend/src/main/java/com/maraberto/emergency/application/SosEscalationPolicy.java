package com.maraberto.emergency.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Escada de escalonamento do SOS (ADR-018).
 *
 * <p>O despacho inicial usa o raio de cobertura do posto/base. Quando ninguém
 * assume, o chamado é reaberto para anéis cada vez maiores — {@code 5, 7, 10,
 * 12, 15, 20 km} por padrão — até o teto da escada. O anel é absoluto: ele
 * ignora o {@code coverage_radius_m} cadastrado, porque nesse momento o que
 * importa é quem consegue chegar, não quem está na área formal do posto.
 *
 * <p>O teto por posto/base continua sendo {@code maraberto.dispatch.max-radius-m}
 * (15 km) e vale só para o despacho inicial e para o KNN de fallback. A escada
 * é a última tentativa antes de acionar a coordenação: ela não substitui o raio
 * do posto no dia a dia, apenas amplia quando o chamado não foi assumido.
 */
@Component
public class SosEscalationPolicy {

    private final List<Integer> ringsMeters;
    private final Duration delay;

    public SosEscalationPolicy(
            @Value("${maraberto.dispatch.escalation.rings:5000,7000,10000,12000,15000,20000}")
            String ringsCsv,
            @Value("${maraberto.dispatch.escalation.delay:PT20S}") Duration delay) {
        this.ringsMeters = parseRings(ringsCsv);
        this.delay = delay;
    }

    private static List<Integer> parseRings(String ringsCsv) {
        if (ringsCsv == null || ringsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ringsCsv.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .filter(token -> token.chars().allMatch(Character::isDigit))
                .map(Integer::valueOf)
                .filter(meters -> meters > 0)
                .sorted()
                .toList();
    }

    public boolean enabled() {
        return !ringsMeters.isEmpty();
    }

    /** Anéis em ordem crescente de distância, em metros. */
    public List<Integer> ringsMeters() {
        return ringsMeters;
    }

    public Duration delay() {
        return delay;
    }

    public Instant deadlineFrom(Instant now) {
        return now.plus(delay);
    }
}
