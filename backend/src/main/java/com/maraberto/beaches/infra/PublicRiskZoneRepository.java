package com.maraberto.beaches.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maraberto.beaches.domain.RiskPersistence;
import com.maraberto.beaches.domain.RiskType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Zonas de risco visíveis no mapa público (RF02/RF18).
 *
 * <p>A visibilidade combina três fatores na consulta: {@code active}, início da
 * validade e fim da validade. Zonas {@code PERMANENT} nascem sem fim de
 * validade (constraint da V2) e nunca expiram; {@code SEMI_PERMANENT} some ao
 * vencer, sem apagar a linha. A resposta já sai como {@code FeatureCollection}
 * com a cor definida pelo tipo de risco.
 */
@Repository
public class PublicRiskZoneRepository {

    private static final String ZONES_SQL = """
            SELECT z.id,
                   z.name,
                   z.risk_type,
                   z.description,
                   z.persistence,
                   z.valid_until,
                   z.last_confirmed_at,
                   b.id AS beach_id,
                   b.name AS beach_name,
                   ST_AsGeoJSON(z.geometry, 6) AS geojson
              FROM critical_zones z
              JOIN beaches b ON b.id = z.beach_id AND b.active
             WHERE z.active
               AND z.valid_from <= NOW()
               AND (z.valid_until IS NULL OR z.valid_until > NOW())
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PublicRiskZoneRepository(
            NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Zonas visíveis, opcionalmente restritas a um retângulo.
     *
     * @param bbox {@code [west, south, east, north]} ou {@code null} para todas
     */
    public JsonNode findVisible(double[] bbox) {
        String sql = ZONES_SQL;
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (bbox != null) {
            sql += """
                       AND ST_Intersects(
                               z.geometry,
                               ST_MakeEnvelope(:west, :south, :east, :north, 4326))
                    """;
            parameters.addValue("west", bbox[0]);
            parameters.addValue("south", bbox[1]);
            parameters.addValue("east", bbox[2]);
            parameters.addValue("north", bbox[3]);
        }
        sql += " ORDER BY z.name, z.id";

        ObjectNode collection = objectMapper.createObjectNode();
        collection.put("type", "FeatureCollection");
        ArrayNode features = collection.putArray("features");
        jdbc.query(sql, parameters,
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        features.add(feature(rs)));
        return collection;
    }

    private ObjectNode feature(ResultSet rs) throws SQLException {
        RiskType riskType = RiskType.valueOf(rs.getString("risk_type"));
        RiskPersistence persistence = RiskPersistence.valueOf(rs.getString("persistence"));
        UUID beachId = rs.getObject("beach_id", UUID.class);

        ObjectNode feature = objectMapper.createObjectNode();
        feature.put("type", "Feature");
        ObjectNode properties = feature.putObject("properties");
        properties.put("id", rs.getObject("id", UUID.class).toString());
        properties.put("name", rs.getString("name"));
        properties.put("riskType", riskType.name());
        properties.put("persistence", persistence.name());
        properties.put("color", colorOf(riskType));
        properties.put("beachId", beachId.toString());
        properties.put("beachName", rs.getString("beach_name"));
        putNullable(properties, "description", rs.getString("description"));
        putNullable(properties, "validUntil", instant(rs, "valid_until"));
        putNullable(properties, "lastConfirmedAt", instant(rs, "last_confirmed_at"));
        feature.set("geometry", geojson(rs.getString("geojson")));
        return feature;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static void putNullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static void putNullable(ObjectNode node, String field, Instant value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value.toString());
        }
    }

    private JsonNode geojson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Geometria GeoJSON inválida em critical_zones",
                    exception);
        }
    }

    /** Cor estável por tipo de risco, usada pela camada de preenchimento do mapa. */
    private static String colorOf(RiskType riskType) {
        return switch (riskType) {
            case RIP_CURRENT -> "#ef4444";
            case SUBMERGED_DROP_OFF -> "#f97316";
            case STRONG_WAVES -> "#f59e0b";
            case ROCKS_AND_PIERS -> "#7c3aed";
            case WHIRLPOOL_OR_DEEP_POOL -> "#0ea5e9";
            case MARINE_ANIMALS -> "#d946ef";
            case WATER_POLLUTION -> "#65a30d";
            case EXTREME_WEATHER -> "#0f172a";
            case OTHER -> "#64748b";
        };
    }
}
