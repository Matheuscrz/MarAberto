package com.maraberto.emergency.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "emergency_outbox_events")
@Getter
@Setter
public class EmergencyOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    /**
     * Payload JSON cifrado em repouso (AES-256-GCM). O publicador da outbox
     * deve decifrar com {@code SensitiveDataProtector.AAD_EMERGENCY_OUTBOX}
     * antes de desserializar o evento.
     */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    /**
     * Quantidade de tentativas de publicação já realizadas. Ao atingir o
     * limite configurado, o evento vira {@code FAILED} e para de ser
     * reivindicado.
     */
    @Column(nullable = false)
    private int attempts;

    /**
     * Apenas o tipo da falha (por exemplo, {@code IllegalStateException}).
     * Nunca grave payload, coordenadas ou dados pessoais aqui.
     */
    @Column(name = "last_error", length = 200)
    private String lastError;

    public enum OutboxStatus {
        PENDING,
        PUBLISHED,
        FAILED
    }
}
