package com.maraberto.emergency.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Prova de notificação de um SOS para um destinatário.
 *
 * <p>Cada linha é resolvida pelo banco dentro da transação do chamado
 * (ADR-018). Reenviar a mesma linha é idempotente pelo próprio {@code id}; o
 * ack de um destinatário cancela os demais.
 */
@Entity
@Table(name = "emergency_dispatches")
@Getter
@Setter
public class EmergencyDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "emergency_call_id", nullable = false)
    private UUID emergencyCallId;

    /** Rodada de escalonamento: 1 no despacho inicial, 2+ na ampliação. */
    @Column(nullable = false)
    private short round = 1;

    @Column(name = "recipient_post_id")
    private UUID recipientPostId;

    @Column(name = "recipient_base_id")
    private UUID recipientBaseId;

    @Column(name = "recipient_user_id")
    private UUID recipientUserId;

    /** Distância em metros; nula quando o destinatário é a coordenação central. */
    @Column(name = "distance_m")
    private BigDecimal distanceMeters;

    /** Última posição conhecida do plantão acima do limite configurado. */
    @Column(name = "position_stale", nullable = false)
    private boolean positionStale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EmergencyDispatchStatus status;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void acknowledge(Instant when) {
        this.status = EmergencyDispatchStatus.ACKNOWLEDGED;
        this.acknowledgedAt = when;
    }

    public void cancel(Instant when) {
        if (status == EmergencyDispatchStatus.ACKNOWLEDGED) {
            return;
        }
        this.status = EmergencyDispatchStatus.CANCELLED;
        this.cancelledAt = when;
    }
}
