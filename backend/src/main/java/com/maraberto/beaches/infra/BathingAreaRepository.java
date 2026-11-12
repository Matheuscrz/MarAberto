package com.maraberto.beaches.infra;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.io.ByteOrderValues;
import org.locationtech.jts.io.WKBWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persistência da importação de áreas de banho.
 *
 * <p>Todo upsert é por {@code external_id} e preserva o UUID existente, para
 * que postos, zonas, plantões e ocorrências nunca apontem para uma linha
 * recriada. O lote inteiro roda em uma transação do serviço.
 */
@Repository
public class BathingAreaRepository {

    private static final WKBWriter WKB = new WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN, false);

    private final JdbcTemplate jdbc;

    public BathingAreaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AreaRef> findAreas(String stateCode) {
        return jdbc.query("""
                        SELECT id, external_id, name, municipality
                          FROM beaches
                         WHERE state_code = ?
                        """,
                (rs, rowNum) -> new AreaRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("external_id"),
                        rs.getString("name"),
                        rs.getString("municipality")),
                stateCode);
    }

    public Map<String, ExistingSector> findSectors(Collection<String> externalIds) {
        if (externalIds.isEmpty()) {
            return Map.of();
        }
        Map<String, ExistingSector> found = new HashMap<>();
        jdbc.query(con -> {
                    var statement = con.prepareStatement("""
                            SELECT id, beach_id, external_id, name, display_order,
                                   geometry_hash, active
                              FROM beach_sectors
                             WHERE external_id = ANY (?)
                            """);
                    statement.setArray(1, con.createArrayOf("text", externalIds.toArray()));
                    return statement;
                },
                rs -> {
                    String externalId = rs.getString("external_id");
                    found.put(externalId, new ExistingSector(
                            rs.getObject("id", UUID.class),
                            rs.getObject("beach_id", UUID.class),
                            rs.getString("name"),
                            rs.getInt("display_order"),
                            rs.getString("geometry_hash"),
                            rs.getBoolean("active")));
                });
        return found;
    }

    public List<SectorRef> findSectorsByPrefix(String prefix) {
        return jdbc.query("""
                        SELECT id, beach_id, external_id, name, active
                          FROM beach_sectors
                         WHERE external_id LIKE ?
                        """,
                (rs, rowNum) -> new SectorRef(
                        rs.getObject("id", UUID.class),
                        rs.getObject("beach_id", UUID.class),
                        rs.getString("external_id"),
                        rs.getString("name"),
                        rs.getBoolean("active")),
                prefix + "%");
    }

    public UUID insertArea(AreaWrite write) {
        return jdbc.queryForObject("""
                        INSERT INTO beaches
                            (name, municipality, state_code, kind, external_id,
                             source, source_version, geometry_hash, source_metadata,
                             imported_at, imported_by, active, boundary)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), NOW(), ?, TRUE,
                                ST_GeomFromText('MULTIPOLYGON EMPTY', 4326))
                        RETURNING id
                        """,
                UUID.class,
                write.name(), write.municipality(), write.stateCode(), write.kind().name(),
                write.externalId(), write.source(), write.sourceVersion(), write.geometryHash(),
                write.sourceMetadataJson(), write.importedBy());
    }

    public void updateArea(UUID id, AreaWrite write) {
        jdbc.update("""
                        UPDATE beaches
                           SET name = ?, municipality = ?, state_code = ?, kind = ?,
                               external_id = ?, source = ?, source_version = ?,
                               geometry_hash = ?, source_metadata = CAST(? AS jsonb),
                               imported_at = NOW(), imported_by = ?, active = TRUE
                         WHERE id = ?
                        """,
                write.name(), write.municipality(), write.stateCode(), write.kind().name(),
                write.externalId(), write.source(), write.sourceVersion(), write.geometryHash(),
                write.sourceMetadataJson(), write.importedBy(), id);
    }

    public void upsertSector(SectorWrite write) {
        jdbc.update("""
                        INSERT INTO beach_sectors
                            (beach_id, external_id, name, display_order, geometry,
                             geometry_hash, source, source_version, source_metadata,
                             imported_at, imported_by, active)
                        VALUES (?, ?, ?, ?, ST_GeomFromWKB(?, 4326), ?, ?, ?, CAST(? AS jsonb),
                                NOW(), ?, TRUE)
                        ON CONFLICT (external_id) DO UPDATE
                           SET beach_id = EXCLUDED.beach_id,
                               name = EXCLUDED.name,
                               display_order = EXCLUDED.display_order,
                               geometry = EXCLUDED.geometry,
                               geometry_hash = EXCLUDED.geometry_hash,
                               source = EXCLUDED.source,
                               source_version = EXCLUDED.source_version,
                               source_metadata = EXCLUDED.source_metadata,
                               imported_at = NOW(),
                               imported_by = EXCLUDED.imported_by,
                               active = TRUE
                        """,
                write.beachId(), write.externalId(), write.name(), write.displayOrder(),
                wkb(write.geometry()), write.geometryHash(), write.source(),
                write.sourceVersion(), write.sourceMetadataJson(), write.importedBy());
    }

    public int deactivateSectors(Collection<String> externalIds) {
        if (externalIds.isEmpty()) {
            return 0;
        }
        return jdbc.update(con -> {
            var statement = con.prepareStatement("""
                    UPDATE beach_sectors
                       SET active = FALSE
                     WHERE external_id = ANY (?)
                    """);
            statement.setArray(1, con.createArrayOf("text", externalIds.toArray()));
            return statement;
        });
    }

    /**
     * O polígono da área é derivado dos setores ativos (ADR-021). Se todos os
     * setores estiverem inativos, mantém a última geometria conhecida.
     */
    public void recomputeBoundary(UUID beachId) {
        jdbc.update("""
                        UPDATE beaches b
                           SET boundary = COALESCE((
                                   SELECT ST_Multi(ST_CollectionExtract(
                                              ST_UnaryUnion(ST_Collect(s.geometry)), 3))
                                     FROM beach_sectors s
                                    WHERE s.beach_id = b.id
                                      AND s.active
                               ), b.boundary)
                         WHERE b.id = ?
                        """,
                beachId);
    }

    public void insertBatch(BatchWrite write) {
        jdbc.update("""
                        INSERT INTO import_batches
                            (source_file, source_version, state_code, mode, dry_run, applied,
                             created_count, updated_count, unchanged_count, missing_count,
                             rejected_count, imported_by, report)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                        """,
                write.sourceFile(), write.sourceVersion(), write.stateCode(), write.mode(),
                write.dryRun(), write.applied(), write.created(), write.updated(),
                write.unchanged(), write.missing(), write.rejected(), write.importedBy(),
                write.reportJson());
    }

    public boolean isEmpty() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM beaches", Integer.class);
        return count == null || count == 0;
    }

    /** SHA-256 do último arquivo aplicado com sucesso nesta UF, se houver. */
    public Optional<String> lastAppliedSourceVersion(String stateCode) {
        return jdbc.queryForList("""
                        SELECT source_version
                          FROM import_batches
                         WHERE state_code = ?
                           AND applied
                         ORDER BY imported_at DESC
                         LIMIT 1
                        """,
                String.class, stateCode).stream().findFirst();
    }

    private static byte[] wkb(Geometry geometry) {
        return WKB.write(geometry);
    }

    public record AreaRef(UUID id, String externalId, String name, String municipality) {
    }

    public record ExistingSector(
            UUID id,
            UUID beachId,
            String name,
            int displayOrder,
            String geometryHash,
            boolean active) {
    }

    public record SectorRef(
            UUID id, UUID beachId, String externalId, String name, boolean active) {
    }

    public record AreaWrite(
            String externalId,
            String name,
            String municipality,
            String stateCode,
            com.maraberto.beaches.domain.BeachKind kind,
            String geometryHash,
            String source,
            String sourceVersion,
            String sourceMetadataJson,
            UUID importedBy) {
    }

    public record SectorWrite(
            UUID beachId,
            String externalId,
            String name,
            int displayOrder,
            MultiPolygon geometry,
            String geometryHash,
            String source,
            String sourceVersion,
            String sourceMetadataJson,
            UUID importedBy) {
    }

    public record BatchWrite(
            String sourceFile,
            String sourceVersion,
            String stateCode,
            String mode,
            boolean dryRun,
            boolean applied,
            int created,
            int updated,
            int unchanged,
            int missing,
            int rejected,
            UUID importedBy,
            String reportJson) {
    }
}
