package com.maraberto.audit.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Ato a registrar na trilha.
 *
 * <p>O papel do ator é resolvido pela implementação a partir da autenticação
 * corrente (quando houver); o IP, quando informado, é convertido em índice cego
 * antes de ser persistido. O {@code detail} nunca leva PII: ids, nomes de campo
 * e valores não sensíveis.
 */
public record AuditEvent(
        UUID actorId,
        String action,
        String entityType,
        String entityId,
        Map<String, Object> detail,
        String ipAddress) {

    public AuditEvent {
        Objects.requireNonNull(action, "ação da auditoria não pode ser nula");
        Objects.requireNonNull(entityType, "tipo da entidade não pode ser nulo");
        detail = detail == null ? Map.of() : Map.copyOf(detail);
    }

    /** Evento de um operador autenticado. */
    public static AuditEvent of(
            UUID actorId,
            String action,
            String entityType,
            Object entityId,
            Map<String, Object> detail) {
        return new AuditEvent(actorId, action, entityType, text(entityId), detail, null);
    }

    /** Evento sem ator humano (job, rotina automática). */
    public static AuditEvent system(
            String action,
            String entityType,
            Object entityId,
            Map<String, Object> detail) {
        return new AuditEvent(null, action, entityType, text(entityId), detail, null);
    }

    /** Mesmo evento com o IP de origem para o índice cego. */
    public AuditEvent withIpAddress(String ipAddress) {
        return new AuditEvent(actorId, action, entityType, entityId, detail, ipAddress);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
