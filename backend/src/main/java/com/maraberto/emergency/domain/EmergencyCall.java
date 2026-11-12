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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "emergency_calls")
@Getter
@Setter
public class EmergencyCall {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_request_id", nullable = false, unique = true, length = 100)
    private String clientRequestId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "accuracy_meters")
    private Double accuracyMeters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EmergencyCallStatus status;

    /** Posto que assumiu o chamado; nulo enquanto ninguém deu ack ou se a base móvel assumiu. */
    @Column(name = "assigned_post_id")
    private UUID assignedPostId;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    /**
     * Anéis de escalonamento já consumidos. Zero enquanto só o despacho
     * inicial (round 1) foi feito; cada abertura de anel amplia o raio e
     * incrementa o contador (ADR-018).
     */
    @Column(name = "escalation_step", nullable = false)
    private short escalationStep;

    /**
     * Próximo horário de abertura de anel. {@code null} encerra o
     * escalonamento do chamado — porque alguém assumiu, porque a escada
     * esgotou ou porque a instalação não configurou anéis.
     */
    @Column(name = "next_escalation_at")
    private Instant nextEscalationAt;

    /**
     * Versão usada pelo ack por compare-and-set. Não é optimistic locking do
     * Hibernate: a transição de estado é feita por {@code UPDATE ... WHERE
     * status = 'DISPATCHED'}, que não depende do objeto em memória.
     */
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
