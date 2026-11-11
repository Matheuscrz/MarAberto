package com.maraberto.emergency.infra;

import com.maraberto.emergency.domain.EmergencyDispatch;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmergencyDispatchRepository extends JpaRepository<EmergencyDispatch, UUID> {

    Optional<EmergencyDispatch> findFirstByEmergencyCallIdAndRecipientUserIdOrderByCreatedAtDesc(
            UUID emergencyCallId,
            UUID recipientUserId);

    List<EmergencyDispatch> findByEmergencyCallIdAndStatusIn(
            UUID emergencyCallId,
            Collection<com.maraberto.emergency.domain.EmergencyDispatchStatus> statuses);

    /**
     * Cancela os despachos perdedores do ack. Não toca no vencedor nem em quem
     * já tinha assumido.
     */
    @Modifying
    @Query(value = """
            UPDATE emergency_dispatches
            SET status = 'CANCELLED', cancelled_at = :cancelledAt, updated_at = :cancelledAt
            WHERE emergency_call_id = :callId
              AND id <> :winnerDispatchId
              AND status IN ('PENDING', 'NOTIFIED')
            """, nativeQuery = true)
    int cancelOthers(
            @Param("callId") UUID callId,
            @Param("winnerDispatchId") UUID winnerDispatchId,
            @Param("cancelledAt") Instant cancelledAt);
}
