package com.maraberto.operations.application;

import com.maraberto.media.api.MediaCatalog;
import com.maraberto.operations.api.CreateStationRequest;
import com.maraberto.operations.api.PublicStation;
import com.maraberto.operations.api.StationAdminView;
import com.maraberto.operations.api.StationPositionRequest;
import com.maraberto.operations.domain.StationKind;
import com.maraberto.operations.domain.StationNotFoundException;
import com.maraberto.operations.domain.StationOutsideShiftException;
import com.maraberto.operations.domain.StationPositionSource;
import com.maraberto.operations.infra.PublicOperationsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estruturas operacionais: leitura pública, cadastro em campo e
 * ativação/reposição no painel.
 *
 * <p>Desativar não apaga nada: plantões e ocorrências continuam apontando para
 * a mesma linha. O que muda é a elegibilidade — posto inativo some do mapa
 * público e da lista de destinos do plantão. A posição pode ser cadastrada e
 * reposta com o GPS do aparelho (RF11); a origem fica registrada.
 *
 * <p>Quem está em campo também desativa a própria estrutura: se o posto fechou
 * ou a base móvel vai sair da praia, o pin sai do mapa na hora, sem esperar a
 * TI. A coordenação e a TI podem alternar qualquer estrutura.
 */
@Service
public class StationService {

    /** Teto de fotos por estrutura, o mesmo da sinalização. */
    private static final int MAX_PHOTOS = 3;

    private final PublicOperationsRepository publicRepository;
    private final MediaCatalog mediaCatalog;
    private final NamedParameterJdbcTemplate jdbc;

    public StationService(
            PublicOperationsRepository publicRepository,
            MediaCatalog mediaCatalog,
            NamedParameterJdbcTemplate jdbc) {
        this.publicRepository = publicRepository;
        this.mediaCatalog = mediaCatalog;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PublicStation> publicStations() {
        return publicRepository.findStations();
    }

    /** Estrutura única na visão do operador (inclusive desativada). */
    @Transactional(readOnly = true)
    public StationAdminView station(StationKind kind, UUID id) {
        return findAdmin(kind, id).orElseThrow(StationNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<StationAdminView> adminStations() {
        return jdbc.query("""
                        SELECT p.id,
                               'FIXED' AS kind,
                               p.name,
                               p.beach_id,
                               b.name AS beach_name,
                               ST_Y(p.location::geometry) AS latitude,
                               ST_X(p.location::geometry) AS longitude,
                               p.position_source,
                               p.position_accuracy_m,
                               p.position_updated_at,
                               p.active,
                               p.deactivated_at,
                               p.status_note,
                               ARRAY(
                                   SELECT '/api/v1/media/' || pm.media_id
                                     FROM lifeguard_post_media pm
                                    WHERE pm.post_id = p.id
                                    ORDER BY pm.display_order, pm.media_id
                               ) AS photo_urls
                          FROM lifeguard_posts p
                          JOIN beaches b ON b.id = p.beach_id
                        UNION ALL
                        SELECT m.id,
                               'MOBILE' AS kind,
                               m.name,
                               NULL::uuid AS beach_id,
                               NULL::text AS beach_name,
                               ST_Y(m.location::geometry) AS latitude,
                               ST_X(m.location::geometry) AS longitude,
                               m.position_source,
                               m.position_accuracy_m,
                               m.position_updated_at,
                               m.active,
                               m.deactivated_at,
                               m.status_note,
                               ARRAY(
                                   SELECT '/api/v1/media/' || mm.media_id
                                     FROM mobile_base_media mm
                                    WHERE mm.base_id = m.id
                                    ORDER BY mm.display_order, mm.media_id
                               ) AS photo_urls
                          FROM mobile_bases m
                         ORDER BY kind, name
                        """,
                (rs, rowNum) -> adminView(rs));
    }

    /**
     * Cadastra a estrutura na posição atual do aparelho (RF11).
     *
     * <p>Posto fixo exige a área de banho: ele pertence ao mapeamento e é o
     * destino do plantão. Base móvel não tem praia — ela aparece no mapa
     * enquanto houver plantão vivo nela. As fotos já foram enviadas pelo mesmo
     * operador e são anexadas depois do cadastro, na ordem em que chegaram.
     */
    @Transactional
    public StationAdminView createStation(UUID userId, CreateStationRequest request) {
        List<UUID> mediaIds = validatedMediaIds(userId, request.mediaIds());
        if (request.kind() == StationKind.FIXED) {
            if (request.beachId() == null) {
                throw new IllegalArgumentException(
                        "Posto fixo precisa pertencer a uma área de banho");
            }
            requireActiveBeach(request.beachId());
            UUID createdId = jdbc.queryForObject("""
                            INSERT INTO lifeguard_posts
                                (beach_id, name, location, active, position_source,
                                 position_accuracy_m, position_updated_at)
                            VALUES (:beachId, :name,
                                    CAST(ST_SetSRID(ST_MakePoint(
                                            CAST(:longitude AS double precision),
                                            CAST(:latitude AS double precision)), 4326)
                                        AS geography),
                                    TRUE, 'DEVICE_GPS',
                                    CAST(:accuracy AS numeric(7, 1)), NOW())
                            RETURNING id
                            """,
                    positionParameters(request.latitude(), request.longitude())
                            .addValue("beachId", request.beachId())
                            .addValue("name", request.name().trim())
                            .addValue("accuracy", request.accuracyMeters()),
                    UUID.class);
            attachMedia(StationKind.FIXED, createdId, mediaIds);
            return findAdmin(StationKind.FIXED, createdId)
                    .orElseThrow(StationNotFoundException::new);
        }

        UUID createdId = jdbc.queryForObject("""
                        INSERT INTO mobile_bases
                            (name, location, active, position_source,
                             position_accuracy_m, position_updated_at)
                        VALUES (:name,
                                CAST(ST_SetSRID(ST_MakePoint(
                                        CAST(:longitude AS double precision),
                                        CAST(:latitude AS double precision)), 4326)
                                    AS geography),
                                TRUE, 'DEVICE_GPS',
                                CAST(:accuracy AS numeric(7, 1)), NOW())
                        RETURNING id
                        """,
                positionParameters(request.latitude(), request.longitude())
                        .addValue("name", request.name().trim())
                        .addValue("accuracy", request.accuracyMeters()),
                UUID.class);
        attachMedia(StationKind.MOBILE, createdId, mediaIds);
        return findAdmin(StationKind.MOBILE, createdId)
                .orElseThrow(StationNotFoundException::new);
    }

    /** Reposiciona a estrutura com a leitura atual do aparelho (RF11). */
    @Transactional
    public StationAdminView moveStation(
            StationKind kind, UUID id, StationPositionRequest request) {
        String table = tableOf(kind);
        int updated = jdbc.update("""
                        UPDATE %s
                           SET location = CAST(ST_SetSRID(ST_MakePoint(
                                   CAST(:longitude AS double precision),
                                   CAST(:latitude AS double precision)), 4326) AS geography),
                               position_source = 'DEVICE_GPS',
                               position_accuracy_m = CAST(:accuracy AS numeric(7, 1)),
                               position_updated_at = NOW(),
                               updated_at = NOW()
                         WHERE id = :id
                        """.formatted(table),
                positionParameters(request.latitude(), request.longitude())
                        .addValue("id", id)
                        .addValue("accuracy", request.accuracyMeters()));
        if (updated == 0) {
            throw new StationNotFoundException();
        }
        return findAdmin(kind, id).orElseThrow(StationNotFoundException::new);
    }

    /**
     * Alterna a estrutura a partir do console de campo.
     *
     * <p>O guarda-vidas só mexe na estrutura do próprio plantão aberto: é o
     * posto/base onde ele está, e desativar não apaga o turno nem a ocorrência
     * em andamento. A coordenação e a TI podem alternar qualquer estrutura.
     */
    @Transactional
    public StationAdminView setActiveInField(
            UUID userId,
            boolean privileged,
            StationKind kind,
            UUID id,
            boolean active,
            String statusNote) {
        findAdmin(kind, id).orElseThrow(StationNotFoundException::new);
        if (!privileged && !ownedByOpenShift(userId, kind, id)) {
            throw new StationOutsideShiftException();
        }
        return setActive(kind, id, active, statusNote);
    }

    /**
     * Liga/desliga a estrutura. Desligar carimba {@code deactivated_at} na
     * primeira vez e guarda o motivo em {@code status_note}; religar limpa a
     * data, mas mantém a nota para o histórico do painel.
     */
    @Transactional
    public StationAdminView setActive(
            StationKind kind, UUID id, boolean active, String statusNote) {
        String table = tableOf(kind);
        int updated = jdbc.update("""
                        UPDATE %s
                           SET active = :active,
                               deactivated_at = CASE
                                   WHEN :active THEN NULL
                                   ELSE COALESCE(deactivated_at, NOW())
                               END,
                               status_note = CASE
                                   WHEN :active
                                       THEN COALESCE(CAST(:note AS text), status_note)
                                   ELSE CAST(:note AS text)
                               END,
                               updated_at = NOW()
                         WHERE id = :id
                        """.formatted(table),
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("active", active)
                        .addValue("note", statusNote));
        if (updated == 0) {
            throw new StationNotFoundException();
        }
        return findAdmin(kind, id).orElseThrow(StationNotFoundException::new);
    }

    private java.util.Optional<StationAdminView> findAdmin(StationKind kind, UUID id) {
        String sql = kind == StationKind.FIXED
                ? """
                  SELECT p.id,
                         'FIXED' AS kind,
                         p.name,
                         p.beach_id,
                         b.name AS beach_name,
                         ST_Y(p.location::geometry) AS latitude,
                         ST_X(p.location::geometry) AS longitude,
                         p.position_source,
                         p.position_accuracy_m,
                         p.position_updated_at,
                         p.active,
                         p.deactivated_at,
                         p.status_note,
                         ARRAY(
                             SELECT '/api/v1/media/' || pm.media_id
                               FROM lifeguard_post_media pm
                              WHERE pm.post_id = p.id
                              ORDER BY pm.display_order, pm.media_id
                         ) AS photo_urls
                    FROM lifeguard_posts p
                    JOIN beaches b ON b.id = p.beach_id
                   WHERE p.id = :id
                  """
                : """
                  SELECT m.id,
                         'MOBILE' AS kind,
                         m.name,
                         NULL::uuid AS beach_id,
                         NULL::text AS beach_name,
                         ST_Y(m.location::geometry) AS latitude,
                         ST_X(m.location::geometry) AS longitude,
                         m.position_source,
                         m.position_accuracy_m,
                         m.position_updated_at,
                         m.active,
                         m.deactivated_at,
                         m.status_note,
                         ARRAY(
                             SELECT '/api/v1/media/' || mm.media_id
                               FROM mobile_base_media mm
                              WHERE mm.base_id = m.id
                              ORDER BY mm.display_order, mm.media_id
                         ) AS photo_urls
                    FROM mobile_bases m
                   WHERE m.id = :id
                  """;
        return jdbc.query(sql,
                new MapSqlParameterSource("id", id),
                (rs, rowNum) -> adminView(rs)).stream().findFirst();
    }

    /** O posto/base é o alvo do plantão aberto desta pessoa? */
    private boolean ownedByOpenShift(UUID userId, StationKind kind, UUID id) {
        Boolean owns = jdbc.query("""
                        SELECT EXISTS (
                            SELECT 1
                              FROM shift_sessions s
                             WHERE s.user_id = :userId
                               AND s.ended_at IS NULL
                               AND (
                                   (:kind = 'FIXED' AND s.post_id = :id)
                                   OR (:kind = 'MOBILE' AND s.mobile_base_id = :id)
                               )
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("kind", kind.name())
                        .addValue("id", id),
                rs -> rs.next() ? rs.getBoolean(1) : false);
        return Boolean.TRUE.equals(owns);
    }

    private void attachMedia(StationKind kind, UUID stationId, List<UUID> mediaIds) {
        if (mediaIds.isEmpty()) {
            return;
        }
        String table = kind == StationKind.FIXED ? "lifeguard_post_media" : "mobile_base_media";
        String column = kind == StationKind.FIXED ? "post_id" : "base_id";
        for (int index = 0; index < mediaIds.size(); index++) {
            jdbc.update("""
                            INSERT INTO %s (%s, media_id, display_order)
                            VALUES (:stationId, :mediaId, :displayOrder)
                            ON CONFLICT (%s, media_id) DO NOTHING
                            """.formatted(table, column, column),
                    new MapSqlParameterSource()
                            .addValue("stationId", stationId)
                            .addValue("mediaId", mediaIds.get(index))
                            .addValue("displayOrder", index));
        }
    }

    private List<UUID> validatedMediaIds(UUID userId, List<UUID> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        Set<UUID> distinct = new LinkedHashSet<>(requested);
        if (distinct.size() > MAX_PHOTOS) {
            throw new IllegalArgumentException("Uma estrutura aceita no máximo 3 fotos");
        }
        Set<UUID> owned = mediaCatalog.findOwned(userId, distinct);
        if (!owned.containsAll(distinct)) {
            throw new IllegalArgumentException(
                    "Uma das fotos não existe ou foi enviada por outro operador");
        }
        return List.copyOf(distinct);
    }

    private void requireActiveBeach(UUID beachId) {
        Boolean active = jdbc.query(
                "SELECT active FROM beaches WHERE id = :id",
                new MapSqlParameterSource("id", beachId),
                rs -> rs.next() ? rs.getBoolean("active") : null);
        if (!Boolean.TRUE.equals(active)) {
            throw new IllegalArgumentException("Área de banho inexistente ou inativa");
        }
    }

    private static MapSqlParameterSource positionParameters(double latitude, double longitude) {
        return new MapSqlParameterSource()
                .addValue("latitude", latitude)
                .addValue("longitude", longitude);
    }

    private static String tableOf(StationKind kind) {
        return kind == StationKind.FIXED ? "lifeguard_posts" : "mobile_bases";
    }

    private static Instant instant(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Double accuracy(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        BigDecimal value = rs.getObject(column, BigDecimal.class);
        return value == null ? null : value.doubleValue();
    }

    private static List<String> photoUrls(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Array array = rs.getArray("photo_urls");
        if (array == null) {
            return List.of();
        }
        Object value = array.getArray();
        return value instanceof String[] urls ? List.of(urls) : List.of();
    }

    private static StationAdminView adminView(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        String source = rs.getString("position_source");
        return new StationAdminView(
                rs.getObject("id", UUID.class),
                StationKind.valueOf(rs.getString("kind")),
                rs.getString("name"),
                rs.getObject("beach_id", UUID.class),
                rs.getString("beach_name"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                source == null ? null : StationPositionSource.valueOf(source),
                accuracy(rs, "position_accuracy_m"),
                instant(rs, "position_updated_at"),
                rs.getBoolean("active"),
                instant(rs, "deactivated_at"),
                rs.getString("status_note"),
                photoUrls(rs));
    }
}
