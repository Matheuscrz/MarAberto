package com.maraberto.emergency.api;

import java.util.UUID;

/**
 * Porta pública para encerrar o chamado do SOS a partir do desfecho da
 * ocorrência operacional (ADR-013).
 *
 * <p>O módulo {@code occurrences} é dono do encerramento do atendimento; o
 * {@code emergency} não pode depender dele. Por isso a transição de estado do
 * chamado fica atrás desta porta, implementada dentro de {@code emergency}.
 */
public interface SosCallCloser {

    /**
     * Encerra o chamado se ele ainda estiver em atendimento.
     *
     * <p>É idempotente: chamado já encerrado (ou que nunca foi assumido) não é
     * alterado. A operação participa da transação de quem chama.
     */
    void closeFromOccurrence(UUID callId, SosCallOutcome outcome, UUID actorId);
}
