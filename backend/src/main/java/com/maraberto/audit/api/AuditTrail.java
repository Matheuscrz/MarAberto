package com.maraberto.audit.api;

import java.util.Map;
import java.util.UUID;

/**
 * Porta pública de escrita da trilha.
 *
 * <p>A chamada é síncrona e participa da transação do ato auditado: se a
 * auditoria falhar, o ato também falha. Um evento assíncrono poderia se perder
 * entre o commit e a escrita, e a pergunta "quem fez o quê" ficaria sem prova.
 */
public interface AuditTrail {

    void record(AuditEvent event);

    /** Atalho para o caso comum: ator + ação + entidade + detalhe. */
    default void record(
            UUID actorId,
            String action,
            String entityType,
            Object entityId,
            Map<String, Object> detail) {
        record(AuditEvent.of(actorId, action, entityType, entityId, detail));
    }
}
