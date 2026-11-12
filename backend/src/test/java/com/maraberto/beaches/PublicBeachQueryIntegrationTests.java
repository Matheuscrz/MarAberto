package com.maraberto.beaches;

import static com.maraberto.support.KmlFixtures.feature;
import static com.maraberto.support.KmlFixtures.kml;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.maraberto.beaches.api.BeachDetail;
import com.maraberto.beaches.api.BeachMap;
import com.maraberto.beaches.api.BeachSummary;
import com.maraberto.beaches.api.NearbyBeaches;
import com.maraberto.beaches.application.BeachMapQueryService;
import com.maraberto.beaches.application.BeachNotFoundException;
import com.maraberto.beaches.application.BathingAreaImportService;
import com.maraberto.beaches.application.BathingAreaImportService.ImportCommand;
import com.maraberto.beaches.application.ImportMode;
import com.maraberto.beaches.application.InvalidLocationException;
import com.maraberto.support.PostgisIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lista pública por proximidade e ficha de setores.
 *
 * <p>O mapa é importado de verdade (e a projeção materializada é atualizada),
 * porque a consulta lê `beach_map_features` e não as tabelas cruas.
 */
@SpringBootTest
class PublicBeachQueryIntegrationTests extends PostgisIntegrationTest {

    private static final String PREFIX = "se-areas-banho:";

    @Autowired
    private BathingAreaImportService importService;

    @Autowired
    private BeachMapQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanImportedMap() {
        jdbcTemplate.update("DELETE FROM beach_sectors WHERE external_id LIKE ?", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM import_batches WHERE state_code = 'SE'");
        jdbcTemplate.update("DELETE FROM beaches WHERE external_id LIKE ?", PREFIX + "%");
    }

    @Test
    void listsOnlyAreasWithinThirtyKilometresOrderedByDistance() {
        importFile(kml(
                // Área a poucos metros de quem consulta.
                feature("1", "Praia Perto", "Setor Norte", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia Perto", "Setor Sul", "Aracaju", null, -35.008, -10.010),
                // Área a mais de 200 km (fica fora do raio).
                feature("3", "Praia Longe", "Setor Único", "Canindé", null, -37.800, -9.900)));

        NearbyBeaches nearby = queryService.nearby(-10.0095, -35.0095, null);

        assertThat(nearby.radiusMeters()).isEqualTo(BeachMapQueryService.MAX_RADIUS_METERS);
        assertThat(nearby.beaches())
                .extracting(BeachSummary::name)
                .containsExactly("Praia Perto");
        assertThat(nearby.beaches().getFirst().sectorCount()).isEqualTo(2);
        assertThat(nearby.beaches().getFirst().distanceMeters()).isLessThan(5);
        assertThat(nearby.beaches().getFirst().kind()).hasToString("BEACH");
    }

    @Test
    void smallerRadiusExcludesAnAreaThatTheDefaultRadiusKeeps() {
        importFile(kml(
                feature("1", "Praia Perto", "Setor Único", "Aracaju", null, -35.010, -10.010),
                // Cerca de 2,2 km ao norte da primeira.
                feature("2", "Praia Média", "Setor Único", "Aracaju", null, -35.010, -9.990)));

        NearbyBeaches defaultRadius = queryService.nearby(-10.0105, -35.0105, null);
        assertThat(defaultRadius.beaches()).hasSize(2);

        NearbyBeaches tight = queryService.nearby(-10.0105, -35.0105, 1000);
        assertThat(tight.beaches())
                .extracting(BeachSummary::name)
                .containsExactly("Praia Perto");
    }

    @Test
    void detailKeepsSectorOrderAndTheUnnamedSectorAsNull() {
        importFile(kml(
                feature("1", "Praia Perto", "Setor Norte", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia Perto", "-", "Aracaju", null, -35.008, -10.010)));

        UUID beachId = jdbcTemplate.queryForObject(
                "SELECT id FROM beaches WHERE external_id = ?", UUID.class, PREFIX + "area:1");
        BeachDetail detail = queryService.detail(beachId);

        assertThat(detail.name()).isEqualTo("Praia Perto");
        assertThat(detail.sectors()).hasSize(2);
        assertThat(detail.sectors().get(0).displayOrder()).isEqualTo(1);
        assertThat(detail.sectors().get(0).name()).isEqualTo("Setor Norte");
        // Setor sem nome continua nulo: a UI é que decide mostrar "Setor 2".
        assertThat(detail.sectors().get(1).name()).isNull();
        assertThat(detail.sectors().get(1).displayOrder()).isEqualTo(2);
    }

    @Test
    void cacheIsInvalidatedWhenTheMapIsImportedAgain() {
        importFile(kml(
                feature("1", "Praia Perto", "Setor Único", "Aracaju", null, -35.010, -10.010)));

        assertThat(queryService.nearby(-10.0105, -35.0105, null).beaches()).hasSize(1);

        // A importação publica o evento que limpa o cache e atualiza a view.
        importFile(kml(
                feature("1", "Praia Perto", "Setor Único", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia Nova", "Setor Único", "Aracaju", null, -35.020, -10.020)));

        assertThat(queryService.nearby(-10.0105, -35.0105, null).beaches())
                .extracting(BeachSummary::name)
                .contains("Praia Nova");
    }

    @Test
    void requiresPositionAndCapsTheRadius() {
        assertThatThrownBy(() -> queryService.nearby(null, -35.0, null))
                .isInstanceOf(InvalidLocationException.class)
                .hasMessageContaining("latitude");
        assertThatThrownBy(() -> queryService.nearby(-10.0, null, null))
                .isInstanceOf(InvalidLocationException.class)
                .hasMessageContaining("longitude");
        assertThatThrownBy(() -> queryService.nearby(-10.0, -35.0, 30_001))
                .isInstanceOf(InvalidLocationException.class)
                .hasMessageContaining("30000");
        assertThatThrownBy(() -> queryService.nearby(-91.0, -35.0, null))
                .isInstanceOf(InvalidLocationException.class)
                .hasMessageContaining("Latitude");
    }

    @Test
    void unknownAreaIsReportedAsNotFound() {
        assertThatThrownBy(() -> queryService.detail(UUID.randomUUID()))
                .isInstanceOf(BeachNotFoundException.class);
        assertThatThrownBy(() -> queryService.map(UUID.randomUUID()))
                .isInstanceOf(BeachNotFoundException.class);
    }

    @Test
    void positionResolvesTheAreaAndSectorThatContainTheDevice() {
        importFile(kml(
                feature("1", "Praia Perto", "Setor Norte", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia Perto", "Setor Sul", "Aracaju", null, -35.030, -10.030)));

        NearbyBeaches nearby = queryService.nearby(-10.0095, -35.0095, null);

        assertThat(nearby.position()).isNotNull();
        assertThat(nearby.position().beachName()).isEqualTo("Praia Perto");
        assertThat(nearby.position().sectorLabel()).isEqualTo("Setor Norte");
        assertThat(nearby.position().beachId()).isEqualTo(nearby.beaches().getFirst().id());
    }

    @Test
    void positionIsNullOutsideTheMappedSectors() {
        importFile(kml(
                feature("1", "Praia Perto", "Setor Único", "Aracaju", null, -35.010, -10.010)));

        // Dentro do raio de 30 km, mas fora do polígono de qualquer setor.
        NearbyBeaches nearby = queryService.nearby(-9.9000, -35.2000, null);

        assertThat(nearby.position()).isNull();
    }

    @Test
    void overlappingSectorsResolveToTheFirstInTheMappingOrder() {
        // Dois setores com o mesmo polígono: vence o de menor display_order,
        // a mesma regra da amostra de ocupação.
        importFile(kml(
                feature("1", "Praia Perto", "Setor A", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia Perto", "Setor B", "Aracaju", null, -35.010, -10.010)));

        NearbyBeaches nearby = queryService.nearby(-10.0095, -35.0095, null);

        assertThat(nearby.position()).isNotNull();
        assertThat(nearby.position().sectorLabel()).isEqualTo("Setor A");
    }

    @Test
    void unnamedSectorStillGetsALabelFromItsOrder() {
        importFile(kml(
                feature("1", "Praia Perto", "-", "Aracaju", null, -35.010, -10.010)));

        NearbyBeaches nearby = queryService.nearby(-10.0095, -35.0095, null);

        assertThat(nearby.position()).isNotNull();
        assertThat(nearby.position().sectorLabel()).isEqualTo("Setor 1");
    }

    @Test
    void mapExposesTheSectorGeometryAndTheBoundingBox() {
        importFile(kml(
                feature("1", "Praia Perto", "Setor Norte", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia Perto", "Setor Sul", "Aracaju", null, -35.030, -10.030)));

        UUID beachId = jdbcTemplate.queryForObject(
                "SELECT id FROM beaches WHERE external_id = ?", UUID.class, PREFIX + "area:1");
        BeachMap map = queryService.map(beachId);

        assertThat(map.sectors()).hasSize(2);
        // O PostGIS emite "Polygon" quando o MultiPolygon tem uma única parte.
        assertThat(map.geometry().get("type").asText()).isIn("Polygon", "MultiPolygon");
        assertThat(map.sectors().getFirst().geometry().get("type").asText())
                .isIn("Polygon", "MultiPolygon");

        // O bbox da área é a união dos setores e contém o bbox do setor.
        assertThat(map.bbox().minLon()).isLessThanOrEqualTo(map.sectors().getFirst().bbox().minLon());
        assertThat(map.bbox().maxLat()).isGreaterThanOrEqualTo(
                map.sectors().getFirst().bbox().maxLat());
        assertThat(map.bbox().minLon()).isCloseTo(-35.030, within(1e-6));
        assertThat(map.bbox().maxLon()).isCloseTo(-35.009, within(1e-6));

        // O polígono simplificado continua válido para o desenho.
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT ST_IsValid(geometry)
                          FROM beach_sectors
                         WHERE external_id = ?
                        """, Boolean.class, PREFIX + "1")).isTrue();
    }

    @Test
    void etagIsStableUntilAnotherBatchIsApplied() {
        importFile(kml(
                feature("1", "Praia Perto", "Setor Único", "Aracaju", null, -35.010, -10.010)));
        UUID beachId = jdbcTemplate.queryForObject(
                "SELECT id FROM beaches WHERE external_id = ?", UUID.class, PREFIX + "area:1");

        String first = queryService.etag(beachId);
        assertThat(queryService.etag(beachId)).isEqualTo(first);

        // Um lote aplicado recarrega a projeção: a geometria pode ter mudado.
        importFile(kml(
                feature("1", "Praia Perto", "Setor Único", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia Nova", "Setor Único", "Aracaju", null, -35.020, -10.020)));

        assertThat(queryService.etag(beachId)).isNotEqualTo(first);
    }

    @Test
    void theSameEtagNeverAnswersForAnotherArea() {
        importFile(kml(
                feature("1", "Praia Perto", "Setor Único", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia Nova", "Setor Único", "Aracaju", null, -35.020, -10.020)));
        UUID first = jdbcTemplate.queryForObject(
                "SELECT id FROM beaches WHERE external_id = ?", UUID.class, PREFIX + "area:1");
        UUID second = jdbcTemplate.queryForObject(
                "SELECT id FROM beaches WHERE external_id = ?", UUID.class, PREFIX + "area:2");

        assertThat(queryService.etag(first)).isNotEqualTo(queryService.etag(second));
    }

    private void importFile(String file) {
        importService.importKml(new ImportCommand(
                file.getBytes(StandardCharsets.UTF_8), "areas.kml", "SE", false,
                ImportMode.UPSERT, null));
    }
}
