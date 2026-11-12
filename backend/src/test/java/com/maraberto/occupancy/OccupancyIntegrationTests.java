package com.maraberto.occupancy;

import static com.maraberto.support.KmlFixtures.feature;
import static com.maraberto.support.KmlFixtures.kml;
import static org.assertj.core.api.Assertions.assertThat;

import com.maraberto.beaches.application.BathingAreaImportService;
import com.maraberto.beaches.application.BathingAreaImportService.ImportCommand;
import com.maraberto.beaches.application.ImportMode;
import com.maraberto.occupancy.application.BeachOccupancy;
import com.maraberto.occupancy.application.OccupancyEstimate;
import com.maraberto.occupancy.application.OccupancyService;
import com.maraberto.occupancy.domain.OccupancyLevel;
import com.maraberto.occupancy.domain.OccupancyStatus;
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
 * Ocupação anônima por setor (RF10/RN06/RNF04).
 *
 * <p>O teste confere também o que **não** é gravado: a tabela de amostras não
 * tem coluna de coordenada e o número de amostras não sai da API quando está
 * abaixo do mínimo.
 */
@SpringBootTest
class OccupancyIntegrationTests extends PostgisIntegrationTest {

    private static final String PREFIX = "se-areas-banho:";
    private static final double LATITUDE = -10.0095;
    private static final double LONGITUDE = -35.0095;

    @Autowired
    private BathingAreaImportService importService;

    @Autowired
    private OccupancyService occupancyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanMapAndSamples() {
        jdbcTemplate.update("DELETE FROM occupancy_samples");
        jdbcTemplate.update("DELETE FROM beach_sectors WHERE external_id LIKE ?", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM import_batches WHERE state_code = 'SE'");
        jdbcTemplate.update("DELETE FROM beaches WHERE external_id LIKE ?", PREFIX + "%");
    }

    @Test
    void revealsOnlyTheBandAndOnlyAfterTheMinimumSample() {
        UUID beachId = importBeach();

        // Quatro aparelhos distintos: ainda abaixo do mínimo (RN06).
        for (int index = 0; index < 4; index++) {
            OccupancyEstimate estimate =
                    record("token-" + UUID.randomUUID());
            assertThat(estimate.status()).isEqualTo(OccupancyStatus.INSUFFICIENT);
            assertThat(estimate.level()).isNull();
        }

        // O mesmo token duas vezes na mesma janela conta uma vez só.
        record("token-repetido-00001");
        record("token-repetido-00001");

        OccupancyEstimate estimate = occupancyService.estimate(beachId);
        assertThat(estimate.status()).isEqualTo(OccupancyStatus.ESTIMATE);
        assertThat(estimate.level()).isEqualTo(OccupancyLevel.LOW);
        assertThat(estimate.windowMinutes()).isEqualTo(15);
    }

    @Test
    void samplesOutsideAMappedSectorAreNotStored() {
        importBeach();

        // Fora do polígono importado: resposta vazia e nenhuma linha gravada.
        assertThat(occupancyService.record(-9.5000, -38.5000, "token-fora-000001")).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM occupancy_samples", Integer.class)).isZero();
    }

    @Test
    void oldSamplesFallOutOfTheMovingWindow() {
        UUID beachId = importBeach();
        UUID sectorId = jdbcTemplate.queryForObject(
                "SELECT id FROM beach_sectors WHERE beach_id = ?", UUID.class, beachId);

        // Cinco amostras distintas, mas de 20 minutos atrás: fora da janela.
        for (int index = 0; index < 5; index++) {
            jdbcTemplate.update("""
                            INSERT INTO occupancy_samples
                                (beach_id, sector_id, anonymous_token, bucket_start, created_at)
                            VALUES (?, ?, ?, NOW() - INTERVAL '20 minutes',
                                    NOW() - INTERVAL '20 minutes')
                            """,
                    beachId, sectorId, "token-velho-" + index + "-abcdefgh");
        }

        assertThat(occupancyService.estimate(beachId).status())
                .isEqualTo(OccupancyStatus.INSUFFICIENT);
    }

    @Test
    void theTableNeverStoresACoordinate() {
        assertThat(jdbcTemplate.queryForList("""
                        SELECT column_name
                          FROM information_schema.columns
                         WHERE table_name = 'occupancy_samples'
                        """, String.class))
                .doesNotContain("latitude", "longitude", "token", "ip");
    }

    @Test
    void theAreaIsTheUnionOfTheSectorsAndNeverTheirSum() {
        UUID beachId = importBeachWithTwoSectors();

        // Os mesmos cinco aparelhos aparecem nos dois setores (fronteira).
        // A união continua sendo 5; a soma daria 10 e mudaria a faixa.
        for (int index = 0; index < 5; index++) {
            String token = "token-fronteira-" + index;
            record(-10.0095, -35.0095, token);
            record(-10.0295, -35.0295, token);
        }

        BeachOccupancy occupancy = occupancyService.occupancy(beachId);

        assertThat(occupancy.status()).isEqualTo(OccupancyStatus.ESTIMATE);
        assertThat(occupancy.level()).isEqualTo(OccupancyLevel.LOW);
        assertThat(occupancy.sectors()).hasSize(2);
        assertThat(occupancy.sectors()).allSatisfy(sector -> {
            assertThat(sector.status()).isEqualTo(OccupancyStatus.ESTIMATE);
            assertThat(sector.level()).isEqualTo(OccupancyLevel.LOW);
        });
    }

    @Test
    void aSectorBelowTheMinimumSampleStaysInsufficientWithoutALevel() {
        UUID beachId = importBeachWithTwoSectors();

        // Cinco aparelhos só no primeiro setor.
        for (int index = 0; index < 5; index++) {
            record(-10.0095, -35.0095, "token-norte-" + index);
        }

        BeachOccupancy occupancy = occupancyService.occupancy(beachId);

        assertThat(occupancy.level()).isEqualTo(OccupancyLevel.LOW);
        assertThat(occupancy.sectors()).hasSize(2);
        assertThat(occupancy.sectors().getFirst().status()).isEqualTo(OccupancyStatus.ESTIMATE);
        // O outro setor continua sem amostra: "dados insuficientes", sem nível.
        assertThat(occupancy.sectors().get(1).status()).isEqualTo(OccupancyStatus.INSUFFICIENT);
        assertThat(occupancy.sectors().get(1).level()).isNull();
    }

    private OccupancyEstimate record(String token) {
        return record(LATITUDE, LONGITUDE, token);
    }

    private OccupancyEstimate record(double latitude, double longitude, String token) {
        return occupancyService.record(latitude, longitude, token)
                .orElseThrow(() -> new AssertionError("o setor importado deveria conter o ponto"));
    }

    private UUID importBeach() {
        String file = kml(
                feature("1", "Praia Perto", "Setor Único", "Aracaju", null, -35.010, -10.010));
        importService.importKml(new ImportCommand(
                file.getBytes(StandardCharsets.UTF_8), "areas.kml", "SE", false,
                ImportMode.UPSERT, null));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM beaches WHERE external_id = ?", UUID.class, PREFIX + "area:1");
    }

    /** Duas áreas de setor distantes para exercitar a contagem por setor. */
    private UUID importBeachWithTwoSectors() {
        String file = kml(
                feature("1", "Praia Perto", "Setor Norte", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia Perto", "Setor Sul", "Aracaju", null, -35.030, -10.030));
        importService.importKml(new ImportCommand(
                file.getBytes(StandardCharsets.UTF_8), "areas.kml", "SE", false,
                ImportMode.UPSERT, null));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM beaches WHERE external_id = ?", UUID.class, PREFIX + "area:1");
    }
}
