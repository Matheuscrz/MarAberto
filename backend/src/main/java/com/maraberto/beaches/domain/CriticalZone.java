package com.maraberto.beaches.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Polygon;

/**
 * Zona de risco delimitada no mapa, com persistência temporal explícita.
 *
 * <p>Zonas {@link RiskPersistence#PERMANENT} representam riscos estruturais
 * (píeres, costões, foz de rio). Zonas {@link RiskPersistence#SEMI_PERMANENT}
 * representam riscos topográficos que duram dias ou meses e precisam de
 * validade e revalidação. Correntes migratórias/efêmeras não entram nesta
 * tabela: são demarcações dinâmicas do módulo {@code operations}.
 */
@Entity
@Table(name = "critical_zones")
@Getter
@Setter
public class CriticalZone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beach_id", nullable = false)
    private Beach beach;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RiskType riskType;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskPersistence persistence = RiskPersistence.PERMANENT;

    @Column(nullable = false)
    private Instant validFrom = Instant.now();

    /** Fim da validade; nulo apenas para zonas permanentes. */
    @Column
    private Instant validUntil;

    /** Última confirmação em campo (relevante para zonas semipermanentes). */
    @Column
    private Instant lastConfirmedAt;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(nullable = false, columnDefinition = "geometry(Polygon,4326)")
    private Polygon geometry;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Indica se a zona deve aparecer no mapa no instante informado, combinando
     * o interruptor operacional com a janela de validade.
     */
    public boolean isVisibleAt(Instant instant) {
        return active
                && !validFrom.isAfter(instant)
                && (validUntil == null || validUntil.isAfter(instant));
    }

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
