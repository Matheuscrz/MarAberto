package com.maraberto.occurrences.application;

import com.maraberto.occurrences.api.OccurrenceDetailView;
import com.maraberto.occurrences.api.VictimView;
import com.maraberto.occurrences.infra.OccurrenceRepository;

/**
 * Converte a linha persistida na ficha da API.
 *
 * <p>O detalhe sempre entrega a vítima mascarada: revelar é uma operação
 * separada, com motivo e auditoria.
 */
final class OccurrenceViews {

    private OccurrenceViews() {
    }

    static OccurrenceDetailView detail(OccurrenceRepository.OccurrenceDetail detail) {
        return new OccurrenceDetailView(
                detail.view(),
                detail.summary(),
                detail.outcomeNotes(),
                detail.referenceText(),
                detail.externalProtocol(),
                detail.ciospProtocol(),
                detail.ciospRegisteredAt(),
                detail.closedBy(),
                detail.closedByName(),
                VictimView.hidden(detail.victim().recorded()),
                detail.version());
    }
}
