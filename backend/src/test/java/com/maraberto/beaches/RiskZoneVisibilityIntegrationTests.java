package com.maraberto.beaches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.maraberto.beaches.application.InvalidLocationException;
import com.maraberto.beaches.application.RiskZoneQueryService;
import com.maraberto.support.PostgisIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Visibilidade das zonas de risco no mapa público (RF02/RF18).
 *
 * <p>Zona permanente não expira; semipermanente expira em {@code valid_until}
 * sem apagar a linha; o retângulo é tudo ou nada.
 */
@SpringBootTest
class RiskZoneVisibilityIntegrationTests extends PostgisIntegrationTest {

    private static final String BEACH_NAME = "Praia Zona Teste";
    private static final String POLYGON = "POLYGON(("
            + "-35.010 -10.010, -35.008 -10.010, "
            + "-35.008 -10.008, -35.010 -10.008, "
            + "-35.010 -10.010))";

    @Autowired
    private RiskZoneQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID beachId;

    @BeforeEach
    void createBeach() {
        beachId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO beaches (id, name, municipality, state_code, boundary)
                        VALUES (?, ?, 'Aracaju', 'SE',
                                ST_Multi(ST_GeomFromText(?, 4326)))
                        """, beachId, BEACH_NAME, POLYGON);
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update("DELETE FROM critical_zones WHERE beach_id = ?", beachId);
        jdbcTemplate.update("DELETE FROM beaches WHERE id = ?", beachId);
    }

    @Test
    void aPermanentZoneNeverExpires() {
        insertZone("Foz do rio", "RIP_CURRENT", "PERMANENT", null, null);

        JsonNode zones = queryService.visible(null, null, null, null);

        assertThat(zones.get("type").asText()).isEqualTo("FeatureCollection");
        assertThat(zones.get("features")).hasSize(1);
        JsonNode properties = zones.get("features").get(0).get("properties");
        assertThat(properties.get("persistence").asText()).isEqualTo("PERMANENT");
        assertThat(properties.get("beachName").asText()).isEqualTo(BEACH_NAME);
        assertThat(properties.get("color").asText()).isNotBlank();
        assertThat(properties.get("validUntil").isNull()).isTrue();
    }

    @Test
    void anExpiredSemiPermanentZoneLeavesTheMapWithoutLeavingTheTable() {
        insertZone("Corrente sazonal", "RIP_CURRENT", "SEMI_PERMANENT",
                "NOW() - INTERVAL '2 days'", "NOW() - INTERVAL '1 hour'");

        assertThat(queryService.visible(null, null, null, null).get("features")).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM critical_zones WHERE beach_id = ?",
                Integer.class, beachId)).isEqualTo(1);
    }

    @Test
    void aSemiPermanentZoneInsideItsWindowIsShown() {
        insertZone("Corrente sazonal", "RIP_CURRENT", "SEMI_PERMANENT",
                "NOW() - INTERVAL '1 day'", "NOW() + INTERVAL '1 day'");

        assertThat(queryService.visible(null, null, null, null).get("features")).hasSize(1);
    }

    @Test
    void aZoneThatStartsInTheFutureIsNotShownYet() {
        insertZone("Obra programada", "OTHER", "SEMI_PERMANENT",
                "NOW() + INTERVAL '1 day'", "NOW() + INTERVAL '2 days'");

        assertThat(queryService.visible(null, null, null, null).get("features")).isEmpty();
    }

    @Test
    void theBoundingBoxFiltersButMustBeComplete() {
        insertZone("Foz do rio", "RIP_CURRENT", "PERMANENT", null, null);

        assertThat(queryService.visible(-35.02, -10.02, -35.00, -10.00)
                .get("features")).hasSize(1);
        assertThat(queryService.visible(-40.0, -20.0, -39.0, -19.0)
                .get("features")).isEmpty();

        assertThatThrownBy(() -> queryService.visible(-35.02, null, -35.00, -10.00))
                .isInstanceOf(InvalidLocationException.class);
        assertThatThrownBy(() -> queryService.visible(-35.00, -10.00, -35.02, -10.02))
                .isInstanceOf(InvalidLocationException.class);
    }

    private void insertZone(
            String name, String riskType, String persistence,
            String validFrom, String validUntil) {
        jdbcTemplate.update("""
                        INSERT INTO critical_zones
                            (beach_id, name, risk_type, persistence,
                             valid_from, valid_until, geometry)
                        VALUES (?, ?, ?, ?,
                                %s,
                                %s,
                                ST_GeomFromText(?, 4326))
                        """.formatted(
                        validFrom == null ? "NOW() - INTERVAL '1 day'" : validFrom,
                        validUntil == null ? "NULL" : validUntil),
                beachId, name, riskType, persistence, POLYGON);
    }
}
