package com.maraberto.operations.api;

import java.util.List;

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
     * @param latitude  latitude WGS84 do SOS
     * @param longitude longitude WGS84 do SOS
     */
    List<EligibleResponder> findEligible(double latitude, double longitude);
}
