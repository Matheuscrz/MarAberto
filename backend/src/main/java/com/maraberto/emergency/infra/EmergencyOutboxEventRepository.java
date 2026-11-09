package com.maraberto.emergency.infra;

import com.maraberto.emergency.domain.EmergencyOutboxEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmergencyOutboxEventRepository extends JpaRepository<EmergencyOutboxEvent, UUID> {

    /**
     * Reivindica um lote de eventos pendentes bloqueando as linhas com
     * {@code FOR UPDATE SKIP LOCKED}. Várias instâncias podem rodar o sweep
     * ao mesmo tempo sem publicar o mesmo evento.
     */
    @Query(value = """
            SELECT * FROM emergency_outbox_events
            WHERE status = 'PENDING'
            ORDER BY occurred_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<EmergencyOutboxEvent> claimPendingBatch(@Param("batchSize") int batchSize);

    @Query(value = """
            SELECT * FROM emergency_outbox_events
            WHERE id = :id
            FOR UPDATE
            """, nativeQuery = true)
    Optional<EmergencyOutboxEvent> findForUpdate(@Param("id") UUID id);
}
