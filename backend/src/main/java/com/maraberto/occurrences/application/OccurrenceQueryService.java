package com.maraberto.occurrences.application;

import com.maraberto.occurrences.api.OccurrenceCursor;
import com.maraberto.occurrences.api.OccurrenceDetailView;
import com.maraberto.occurrences.api.OccurrencePage;
import com.maraberto.occurrences.api.OccurrenceView;
import com.maraberto.occurrences.domain.OccurrenceNotFoundException;
import com.maraberto.occurrences.domain.OccurrenceStatus;
import com.maraberto.occurrences.infra.OccurrenceRepository;
import java.time.Instant;
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

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final OccurrenceRepository occurrenceRepository;

    public OccurrenceQueryService(OccurrenceRepository occurrenceRepository) {
        this.occurrenceRepository = occurrenceRepository;
    }

    /**
     * Página de ocorrências, da mais recente para a mais antiga.
     *
     * <p>O cursor é o par {@code (occurredAt, number)} da última linha da
     * página anterior. Keyset (e não offset) mantém a janela estável quando uma
     * ocorrência é encerrada entre duas chamadas: o status muda, a chave não.
     */
    @Transactional(readOnly = true)
    public OccurrencePage list(
            UUID userId,
            boolean allScopes,
            OccurrenceStatus status,
            UUID emergencyCallId,
            Instant beforeOccurredAt,
            Long beforeNumber,
            int limit) {
        if ((beforeOccurredAt == null) != (beforeNumber == null)) {
            throw new IllegalArgumentException(
                    "Informe beforeOccurredAt e beforeNumber juntos para paginar.");
        }
        int size = clamp(limit);
        // Uma linha a mais decide `hasMore` sem uma segunda consulta de count.
        List<OccurrenceView> rows = occurrenceRepository.findAll(
                userId, allScopes, status, emergencyCallId,
                beforeOccurredAt, beforeNumber, size + 1);
        boolean hasMore = rows.size() > size;
        List<OccurrenceView> items = hasMore
                ? List.copyOf(rows.subList(0, size))
                : List.copyOf(rows);
        OccurrenceCursor nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            OccurrenceView last = items.get(items.size() - 1);
            nextCursor = new OccurrenceCursor(last.occurredAt(), last.number());
        }
        return new OccurrencePage(items, nextCursor, hasMore);
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
