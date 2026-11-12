package com.maraberto.operations.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Porta de seleção de quem deve ser notificado em um SOS.
 *
 * <p>A decisão é do banco (PostGIS), dentro da transação do chamado. O
 * transporte nunca filtra destinatário (ADR-018): ele apenas entrega o que esta
 * porta resolveu.
 */
public interface EligibleRespondersPort {

    /**
     * Plantões abertos elegíveis para o ponto do chamado, ordenados por
     * proximidade. Devolve <strong>todos</strong> os que estão dentro do raio de
     * cobertura do seu posto/base; se ninguém estiver no raio, devolve os
     * vizinhos mais próximos (KNN puro); se não houver plantão aberto, devolve
     * lista vazia.
     *
     * <p>É o despacho inicial (round 1), que respeita o raio declarado por cada
     * posto/base.
     *
     * @param latitude  latitude WGS84 do SOS
     * @param longitude longitude WGS84 do SOS
     */
    List<EligibleResponder> findEligible(double latitude, double longitude);

    /**
     * Plantões abertos dentro de um <strong>anel absoluto</strong> de
     * escalonamento, excluindo quem já foi avisado neste chamado.
     *
     * <p>O anel ignora o {@code coverage_radius_m} do posto/base de propósito:
     * ele só é aberto quando ninguém assumiu o chamado, e nesse momento o que
     * importa é "quem consegue chegar", não "quem está dentro da área de
     * cobertura cadastrada". O limite continua sendo o teto do anel, nunca uma
     * notificação sem fim.
     *
     * @param latitude         latitude WGS84 do SOS
     * @param longitude        longitude WGS84 do SOS
     * @param radiusMeters     raio do anel, em metros
     * @param excludedUserIds  operadores que já receberam este chamado
     */
    List<EligibleResponder> findWithinRing(
            double latitude,
            double longitude,
            int radiusMeters,
            Collection<UUID> excludedUserIds);
}
