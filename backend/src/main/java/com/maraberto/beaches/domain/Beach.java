package com.maraberto.beaches.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.MultiPolygon;

/**
 * Área de banho (praia, rio, barragem, lagoa) delimitada em SRID 4326.
 *
 * <p>O nome físico da tabela continua {@code beaches} por compatibilidade com as
 * FKs de postos, zonas, plantões e ocorrências (ADR-021). O polígono é a união
 * dos setores ativos da área.
 */
@Entity
@Table(name = "beaches")
@Getter
@Setter
public class Beach {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 120)
    private String municipality;

    @Column(nullable = false, length = 2)
    private String stateCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BeachKind kind = BeachKind.BEACH;

    @Column(length = 160)
    private String externalId;

    @Column(length = 80)
    private String source;

    @Column(length = 64)
    private String sourceVersion;

    @Column(length = 64)
    private String geometryHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> sourceMetadata;

    private Instant importedAt;

    private UUID importedBy;

    @Column(nullable = false)
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(nullable = false, columnDefinition = "geometry(MultiPolygon,4326)")
    private MultiPolygon boundary;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
