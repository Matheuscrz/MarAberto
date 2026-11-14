package com.maraberto.occurrences.api;

import com.maraberto.occurrences.domain.CiospStatus;
import com.maraberto.occurrences.domain.OccurrenceNature;
import com.maraberto.shared.security.sanitization.SanitizationPolicy;
import com.maraberto.shared.security.sanitization.Sanitized;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Edição da ficha, inclusive depois do encerramento.
 *
 * <p>É o caminho para a coordenação completar um dado que só apareceu depois
 * (vítima identificada, protocolo do CIOSP, correção do resumo). Campo nulo
 * não altera o valor atual; texto vazio limpa o campo quando ele é opcional.
 */
@Schema(name = "UpdateOccurrenceRequest", description = "Correção/complemento da ficha")
public record UpdateOccurrenceRequest(
        OccurrenceNature nature,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 2000)
        String summary,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 2000)
        String outcomeNotes,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 160)
        String referenceText,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 40)
        String externalProtocol,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 40)
        String ciospProtocol,

        CiospStatus ciospStatus,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 120)
        String victimName,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 20)
        String victimAge,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 80)
        String victimContact,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 500)
        String victimCondition) {
}
