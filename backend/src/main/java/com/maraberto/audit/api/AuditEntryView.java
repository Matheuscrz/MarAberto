package com.maraberto.audit.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Linha da trilha como a TI a consulta.
 *
 * <p>O detalhe é um objeto aberto (ids, nomes de campo e valores não
 * sensíveis). A trilha não devolve hash nem índice de IP: eles existem para a
 * verificação de integridade, não para a leitura operacional.
 */
@Schema(name = "AuditEntry", description = "Ato registrado na trilha de auditoria")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEntryView(
        @Schema(description = "Identificador sequencial da trilha")
        long id,
        @Schema(description = "Instante UTC do ato")
        Instant occurredAt,
        @Schema(description = "Operador que executou o ato; nulo em rotinas automáticas")
        UUID actorId,
        @Schema(description = "Papel do ator no momento do ato", example = "ADMIN")
        String actorRole,
        @Schema(description = "Ação registrada", example = "USER_DEACTIVATED")
        String action,
        @Schema(description = "Tipo da entidade afetada", example = "USER")
        String entityType,
        @Schema(description = "Identificador da entidade afetada")
        String entityId,
        @Schema(description = "Identificador da requisição que originou o ato")
        String traceId,
        @Schema(description = "Detalhe sem PII")
        Map<String, Object> detail) {
}
