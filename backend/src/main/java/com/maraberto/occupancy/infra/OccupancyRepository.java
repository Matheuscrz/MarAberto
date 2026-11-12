package com.maraberto.occupancy.infra;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persistência das amostras anônimas de ocupação.
 *
 * <p>A tabela nunca recebe coordenada: a posição é resolvida em setor no
 * momento da escrita e o que fica é {@code setor + janela + HMAC do token}.
 */
@Repository
public class OccupancyRepository {

    private final JdbcTemplate jdbc;

    public OccupancyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Setor ativo que contém o ponto, se houver. */
    public Optional<SectorLocation> findSectorAt(double latitude, double longitude) {
        List<SectorLocation> found = jdbc.query("""
                        SELECT id AS sector_id, beach_id
                          FROM beach_sectors
                         WHERE active
                           AND ST_Contains(geometry, ST_SetSRID(ST_MakePoint(?, ?), 4326))
                         ORDER BY display_order
                         LIMIT 1
                        """,
                (rs, rowNum) -> new SectorLocation(
                        rs.getObject("sector_id", UUID.class),
                        rs.getObject("beach_id", UUID.class)),
                longitude, latitude);
        return found.stream().findFirst();
    }

    /** Uma linha por token/setor/janela: repetir a amostra não infla o contador. */
    public void insertSample(
            UUID beachId, UUID sectorId, OffsetDateTime bucketStart, String tokenHash) {
        jdbc.update("""
                        INSERT INTO occupancy_samples
                            (beach_id, sector_id, anonymous_token, bucket_start)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (anonymous_token, sector_id, bucket_start) DO NOTHING
                        """,
                beachId, sectorId, tokenHash, bucketStart);
    }

    public int countDistinctTokens(UUID beachId, OffsetDateTime since) {
        Integer count = jdbc.queryForObject("""
                        SELECT count(DISTINCT anonymous_token)
                          FROM occupancy_samples
                         WHERE beach_id = ?
                           AND created_at >= ?
                        """,
                Integer.class, beachId, since);
        return count == null ? 0 : count;
    }

    public void purgeOlderThan(OffsetDateTime cutoff) {
        jdbc.update("DELETE FROM occupancy_samples WHERE created_at < ?", cutoff);
    }

    public record SectorLocation(UUID sectorId, UUID beachId) {
    }
}
