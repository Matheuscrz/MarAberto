package com.maraberto.emergency.infra;

import com.maraberto.emergency.domain.EmergencyCall;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmergencyCallRepository extends JpaRepository<EmergencyCall, UUID> {

    Optional<EmergencyCall> findByClientRequestId(String clientRequestId);

    /**
     * Ack por compare-and-set: só o primeiro destinatário que encontrar o
     * chamado em {@code DISPATCHED} vence. Os demais recebem 0 linhas e o
     * serviço responde 409.
     */
    @Modifying
    @Query(value = """
            UPDATE emergency_calls
            SET status = 'ACKNOWLEDGED',
                assigned_post_id = :postId,
                acknowledged_at = :acknowledgedAt,
                updated_at = :acknowledgedAt,
                version = version + 1
            WHERE id = :callId AND status = 'DISPATCHED'
            """, nativeQuery = true)
    int acknowledge(
            @Param("callId") UUID callId,
            @Param("postId") UUID postId,
            @Param("acknowledgedAt") Instant acknowledgedAt);
}
