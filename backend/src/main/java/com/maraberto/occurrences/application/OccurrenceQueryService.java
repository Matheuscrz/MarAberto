package com.maraberto.occurrences.application;

import com.maraberto.occurrences.api.OccurrenceDetailView;
import com.maraberto.occurrences.api.OccurrenceView;
import com.maraberto.occurrences.domain.OccurrenceNotFoundException;
import com.maraberto.occurrences.domain.OccurrenceStatus;
import com.maraberto.occurrences.infra.OccurrenceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura da ocorrência para o console do operador e da coordenação.
 *
 * <p>O escopo é decidido no servidor (RN15): guarda-vidas vê o que ele mesmo
 * atendeu; coordenação e TI veem tudo. O detalhe decifra o resumo do
 * atendimento, mas nunca os dados de vítima.
 */
@Service
public class OccurrenceQueryService {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;

    private final OccurrenceRepository occurrenceRepository;

    public OccurrenceQueryService(OccurrenceRepository occurrenceRepository) {
        this.occurrenceRepository = occurrenceRepository;
    }

    @Transactional(readOnly = true)
    public List<OccurrenceView> list(
            UUID userId, boolean allScopes, OccurrenceStatus status, int limit) {
        return occurrenceRepository.findAll(userId, allScopes, status, clamp(limit));
    }

    @Transactional(readOnly = true)
    public OccurrenceDetailView get(UUID occurrenceId, UUID userId, boolean allScopes) {
        return OccurrenceViews.detail(occurrenceRepository
                .findDetail(occurrenceId, userId, allScopes, false)
                .orElseThrow(() -> new OccurrenceNotFoundException(occurrenceId)));
    }

    private static int clamp(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
