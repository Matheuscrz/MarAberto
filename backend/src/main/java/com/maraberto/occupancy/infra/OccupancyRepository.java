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

    /**
     * Amostras distintas por setor ativo da área, na ordem do mapeamento.
     *
     * <p>O {@code LEFT JOIN} devolve também os setores sem nenhuma amostra
     * (contagem zero), para o cliente mostrar "dados insuficientes" no setor
     * certo em vez de omiti-lo. As contagens por setor <strong>não</strong> são
     * somadas: quem está na fronteira de dois setores conta nos dois, e a área
     * usa a contagem própria (união, não soma).
     */
    public List<SectorSample> countDistinctTokensBySector(UUID beachId, OffsetDateTime since) {
        return jdbc.query("""
                        SELECT s.id AS sector_id,
                               count(DISTINCT o.anonymous_token) AS samples
                          FROM beach_sectors s
                          LEFT JOIN occupancy_samples o
                                 ON o.sector_id = s.id
                                AND o.beach_id = ?
                                AND o.created_at >= ?
                         WHERE s.beach_id = ?
                           AND s.active
                         GROUP BY s.id, s.display_order
                         ORDER BY s.display_order, s.id
                        """,
                (rs, rowNum) -> new SectorSample(
                        rs.getObject("sector_id", UUID.class),
                        rs.getInt("samples")),
                beachId, since, beachId);
    }

    public void purgeOlderThan(OffsetDateTime cutoff) {
        jdbc.update("DELETE FROM occupancy_samples WHERE created_at < ?", cutoff);
    }

    public record SectorLocation(UUID sectorId, UUID beachId) {
    }

    /** Contagem distinta de aparelhos num setor; zero quando ninguém amostrou. */
    public record SectorSample(UUID sectorId, int samples) {
    }
}
