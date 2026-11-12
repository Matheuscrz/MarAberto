package com.maraberto.operations.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

/**
 * Plantão de um operador, em um posto fixo ou em uma base móvel.
 *
 * <p>Só existe um plantão aberto por operador ({@code uq_shift_open}). A última
 * posição é usada apenas para elegibilidade do SOS e para o painel do escopo
 * autorizado; nunca vira rastreamento contínuo (RN17).
 */
@Entity
@Table(name = "shift_sessions")
@Getter
@Setter
public class ShiftSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "mobile_base_id")
    private UUID mobileBaseId;

    @Column(name = "beach_id")
    private UUID beachId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "ended_reason", length = 16)
    private ShiftEndReason endedReason;

    /**
     * Último sinal de vida do console. É o que mantém o plantão aberto: sem
     * sinal dentro de {@code maraberto.dispatch.shift-idle-timeout}, o job de
     * inatividade encerra o turno (ADR-018).
     */
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "last_position", columnDefinition = "geography(Point,4326)")
    private Point lastPosition;

    @Column(name = "last_position_at")
    private Instant lastPositionAt;

    @Column(name = "last_position_accuracy_m")
    private Double lastPositionAccuracyMeters;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isOpen() {
        return endedAt == null;
    }

    /** Marca sinal de vida do console (abertura, posição ou leitura do plantão). */
    public void touch(Instant when) {
        if (endedAt != null) {
            return;
        }
        this.lastSeenAt = when;
    }

    public void end(Instant when, ShiftEndReason reason) {
        if (endedAt != null) {
            return;
        }
        this.endedAt = when.isBefore(startedAt) ? startedAt : when;
        this.endedReason = reason;
    }

    public void moveTo(Point position, Instant when, Double accuracyMeters) {
        this.lastPosition = position;
        this.lastPositionAt = when;
        this.lastPositionAccuracyMeters = accuracyMeters;
        this.lastSeenAt = when;
    }
}
