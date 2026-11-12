package com.maraberto.beaches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maraberto.beaches.application.BathingAreaImportService;
import com.maraberto.beaches.application.BathingAreaImportService.ImportCommand;
import com.maraberto.beaches.application.FeatureAction;
import com.maraberto.beaches.application.FeatureChange;
import com.maraberto.beaches.application.ImportMode;
import com.maraberto.beaches.application.ImportRejectedException;
import com.maraberto.beaches.application.ImportReport;
import com.maraberto.support.PostgisIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Importação e reimportação end-to-end do KML sobre o PostGIS real.
 *
 * <p>Não usa {@code @Transactional} no teste: o serviço precisa comitar para o
 * listener que atualiza a view materializada rodar, como em produção. Por isso
 * cada teste limpa apenas o que ele mesmo importou (prefixo {@code se-}).
 */
@SpringBootTest
class BathingAreaImportIntegrationTests extends PostgisIntegrationTest {

    private static final String PREFIX = "se-areas-banho:";

    @Autowired
    private BathingAreaImportService importService;

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
    void reimportingTheSameFileChangesNothing() {
        String file = kml(
                feature("1", "Praia do Farol", "Setor Norte", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia do Farol", "-", "Aracaju", null, -35.020, -10.010),
                feature("3", "Barragem de Lagarto", "Setor Único", "Lagarto", "SE", -37.010, -10.910));

        ImportReport first = importService.importKml(command(file, false, ImportMode.UPSERT));
        assertThat(first.created()).isEqualTo(3);
        assertThat(first.unchanged()).isZero();
        assertThat(first.rejected()).isZero();

        ImportReport second = importService.importKml(command(file, false, ImportMode.UPSERT));
        assertThat(second.created()).isZero();
        assertThat(second.updated()).isZero();
        assertThat(second.unchanged()).isEqualTo(3);
        assertThat(second.missing()).isZero();

        assertThat(countAreas()).isEqualTo(2);
        assertThat(countSectors()).isEqualTo(3);

        // Setor sem nome vira `name = NULL`, nunca "praia inteira" nem "-".
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM beach_sectors WHERE external_id = ?", String.class, PREFIX + "2"))
                .isNull();
        // Barragem é reconhecida pelo nome da área.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT kind FROM beaches WHERE external_id = ?", String.class,
                PREFIX + "area:1"))
                .isEqualTo("BEACH");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT kind FROM beaches WHERE external_id = ?", String.class,
                PREFIX + "area:3"))
                .isEqualTo("DAM");
        // Um lote aplicado é auditado; o reenvio idêntico faz curto-circuito e
        // não cria lote novo.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM import_batches WHERE state_code = 'SE'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void importsTheRealSergipeMapping() throws Exception {
        Path path = Path.of("../docs/kml/Areas_Banho_Sergipe.kml");
        Assumptions.assumeTrue(Files.isReadable(path),
                "KML real de Sergipe não está no checkout");
        byte[] content = Files.readAllBytes(path);

        ImportReport report = importService.importKml(new ImportCommand(
                content, path.getFileName().toString(), "SE", false, ImportMode.UPSERT, null));

        assertThat(report.created()).isEqualTo(45);
        assertThat(report.rejected()).isZero();
        assertThat(countAreas()).isEqualTo(32);
        assertThat(countSectors()).isEqualTo(45);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM beaches WHERE external_id LIKE ? AND kind = 'DAM'",
                Integer.class, PREFIX + "%"))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM beach_sectors
                 WHERE external_id LIKE ? AND name IS NULL
                """, Integer.class, PREFIX + "%"))
                .isEqualTo(20);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM beach_sectors
                 WHERE external_id LIKE ? AND NOT ST_IsValid(geometry)
                """, Integer.class, PREFIX + "%"))
                .isZero();

        // O arquivo reenviado é o mesmo do lote recém-aplicado: curto-circuito.
        ImportReport again = importService.importKml(new ImportCommand(
                content, path.getFileName().toString(), "SE", false, ImportMode.UPSERT, null));
        assertThat(again.unchanged()).isEqualTo(45);
        assertThat(again.created()).isZero();
    }

    @Test
    void reapplyingAnIdenticalFileSkipsTheWrites() {
        String file = kml(
                feature("1", "Praia do Farol", "Setor Norte", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia do Farol", "Setor Sul", "Aracaju", null, -35.020, -10.010));
        importService.importKml(command(file, false, ImportMode.UPSERT));
        UUID areaId = areaId(PREFIX + "area:1");
        OffsetDateTime importedAt = jdbcTemplate.queryForObject(
                "SELECT imported_at FROM beaches WHERE id = ?", OffsetDateTime.class, areaId);
        int batches = countBatches();

        ImportReport again = importService.importKml(command(file, false, ImportMode.UPSERT));

        // O arquivo idêntico ao último lote aplicado não é reescrito: o
        // relatório mostra tudo inalterado e nenhum lote novo entra na auditoria.
        assertThat(again.unchanged()).isEqualTo(2);
        assertThat(again.created()).isZero();
        assertThat(again.updated()).isZero();
        assertThat(again.applied()).isFalse();
        assertThat(countBatches()).isEqualTo(batches);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT imported_at FROM beaches WHERE id = ?", OffsetDateTime.class, areaId))
                .isEqualTo(importedAt);
    }

    @Test
    void dryRunWritesNothing() {
        String file = kml(feature("1", "Praia do Farol", "Setor Norte", "Aracaju", null,
                -35.010, -10.010));

        ImportReport report = importService.importKml(command(file, true, ImportMode.UPSERT));

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.dryRun()).isTrue();
        assertThat(report.applied()).isFalse();
        assertThat(countAreas()).isZero();
        assertThat(countSectors()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM import_batches WHERE state_code = 'SE'", Integer.class))
                .isZero();
    }

    @Test
    void movingAPolygonUpdatesOnlyThatSectorAndKeepsUuids() {
        String original = kml(
                feature("1", "Praia do Farol", "Setor Norte", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia do Farol", "Setor Sul", "Aracaju", null, -35.020, -10.010));
        importService.importKml(command(original, false, ImportMode.UPSERT));
        UUID areaId = areaId(PREFIX + "area:1");
        UUID movedSectorId = sectorId(PREFIX + "1");
        UUID untouchedSectorId = sectorId(PREFIX + "2");

        String moved = kml(
                feature("1", "Praia do Farol", "Setor Norte", "Aracaju", null, -35.012, -10.012),
                feature("2", "Praia do Farol", "Setor Sul", "Aracaju", null, -35.020, -10.010));
        ImportReport report = importService.importKml(command(moved, false, ImportMode.UPSERT));

        assertThat(report.updated()).isEqualTo(1);
        assertThat(report.unchanged()).isEqualTo(1);
        assertThat(report.changes())
                .filteredOn(change -> change.action() == FeatureAction.UPDATED)
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.externalId()).isEqualTo(PREFIX + "1");
                    assertThat(change.changedFields()).contains("geometry");
                });

        assertThat(areaId(PREFIX + "area:1")).isEqualTo(areaId);
        assertThat(sectorId(PREFIX + "1")).isEqualTo(movedSectorId);
        assertThat(sectorId(PREFIX + "2")).isEqualTo(untouchedSectorId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ST_XMin(Box2D(geometry)) FROM beach_sectors WHERE id = ?",
                Double.class, movedSectorId))
                .isCloseTo(-35.012, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void renamingTheBeachKeepsTheAreaUuid() {
        importService.importKml(command(kml(
                feature("1", "Praia do Farol", "Setor Norte", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia do Farol", "Setor Sul", "Aracaju", null, -35.020, -10.010)),
                false, ImportMode.UPSERT));
        UUID areaId = areaId(PREFIX + "area:1");

        // Renomear a praia no QGIS não pode criar uma área nova: a âncora é o
        // menor id de setor, não o nome (ADR-021).
        ImportReport report = importService.importKml(command(kml(
                feature("1", "Praia do Farol Renovada", "Setor Norte", "Aracaju", null,
                        -35.010, -10.010),
                feature("2", "Praia do Farol Renovada", "Setor Sul", "Aracaju", null,
                        -35.020, -10.010)),
                false, ImportMode.UPSERT));

        assertThat(report.created()).isZero();
        assertThat(report.missing()).isZero();
        assertThat(countAreas()).isEqualTo(1);
        assertThat(areaId(PREFIX + "area:1")).isEqualTo(areaId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM beaches WHERE id = ?", String.class, areaId))
                .isEqualTo("Praia do Farol Renovada");
    }

    @Test
    void syncDeactivatesMissingSectorsAndReactivatesThemWithoutDeletingRows() {
        String twoSectors = kml(
                feature("1", "Praia do Farol", "Setor Norte", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia do Farol", "Setor Sul", "Aracaju", null, -35.020, -10.010));
        importService.importKml(command(twoSectors, false, ImportMode.UPSERT));
        UUID removedSectorId = sectorId(PREFIX + "2");

        String oneSector = kml(
                feature("1", "Praia do Farol", "Setor Norte", "Aracaju", null, -35.010, -10.010));

        // UPSERT nunca remove.
        ImportReport upsert = importService.importKml(command(oneSector, false, ImportMode.UPSERT));
        assertThat(upsert.missing()).isEqualTo(1);
        assertThat(isActive(removedSectorId)).isTrue();

        // SYNC desativa sem apagar a linha (soft delete reversível).
        ImportReport sync = importService.importKml(command(oneSector, false, ImportMode.SYNC));
        assertThat(sync.missing()).isEqualTo(1);
        assertThat(countSectors()).isEqualTo(2);
        assertThat(isActive(removedSectorId)).isFalse();
        // O boundary da área passa a refletir só o setor ativo.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ST_NumGeometries(boundary) = 1 FROM beaches WHERE external_id = ?",
                Boolean.class, PREFIX + "area:1"))
                .isTrue();

        // Voltar o arquivo original reativa o setor sem recriar UUID.
        ImportReport back = importService.importKml(command(twoSectors, false, ImportMode.UPSERT));
        assertThat(back.updated()).isEqualTo(1);
        assertThat(back.changes())
                .filteredOn(change -> change.action() == FeatureAction.UPDATED)
                .singleElement()
                .satisfies(change -> assertThat(change.changedFields()).contains("active"));
        assertThat(isActive(removedSectorId)).isTrue();
        assertThat(sectorId(PREFIX + "2")).isEqualTo(removedSectorId);
    }

    @Test
    void stateParameterIsTheFallbackAndDivergentStateIsRejected() {
        // O arquivo pode não trazer `Estado`: vale a UF informada pelo CBM.
        ImportReport accepted = importService.importKml(command(kml(
                feature("1", "Praia do Farol", "Setor Norte", "Aracaju", null, -35.010, -10.010)),
                false, ImportMode.UPSERT));
        assertThat(accepted.created()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT state_code FROM beaches WHERE external_id = ?", String.class,
                PREFIX + "area:1"))
                .isEqualTo("SE");

        String divergent = kml(
                feature("9", "Praia de Outro Estado", "Setor Único", "Salvador", "BA",
                        -38.500, -13.000));
        assertThatThrownBy(() -> importService.importKml(command(divergent, false, ImportMode.UPSERT)))
                .isInstanceOf(ImportRejectedException.class)
                .hasMessageContaining("BA");

        // Nada do lote rejeitado foi escrito.
        assertThat(countAreas()).isEqualTo(1);
    }

    @Test
    void homonymsInDifferentMunicipalitiesAndSectorsAreDistinct() {
        // Mesmo nome de praia em municípios diferentes: duas áreas.
        // Dois setores com o mesmo nome na mesma praia: dois setores.
        String file = kml(
                feature("1", "Prainha da Orla", "Setor A", "Canindé", null, -37.800, -9.900),
                feature("2", "Prainha da Orla", "Setor A", "Canindé", null, -37.810, -9.900),
                feature("3", "Prainha da Orla", "Setor Único", "Propriá", null, -36.900, -10.200));

        ImportReport report = importService.importKml(command(file, false, ImportMode.UPSERT));

        assertThat(report.created()).isEqualTo(3);
        assertThat(countAreas()).isEqualTo(2);
        assertThat(countSectors()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM beach_sectors s
                  JOIN beaches b ON b.id = s.beach_id
                 WHERE b.municipality = 'Canindé' AND s.name = 'Setor A'
                """, Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                "SELECT name FROM beach_sectors WHERE external_id LIKE ? ORDER BY name",
                String.class, PREFIX + "%"))
                .containsExactly("Setor A", "Setor A", "Setor Único");
    }

    @Test
    void externalIdsCarryTheStatePrefix() {
        importService.importKml(command(kml(
                feature("1", "Praia do Farol", "Setor Norte", "Aracaju", null, -35.010, -10.010)),
                false, ImportMode.UPSERT));

        // Sem o prefixo da UF, o mesmo `id` de um KML de outro estado colidiria.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT external_id FROM beach_sectors WHERE external_id LIKE ?", String.class,
                PREFIX + "%"))
                .isEqualTo("se-areas-banho:1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT external_id FROM beaches WHERE external_id LIKE ?", String.class,
                PREFIX + "area:%"))
                .isEqualTo("se-areas-banho:area:1");
    }

    private ImportCommand command(String file, boolean dryRun, ImportMode mode) {
        return new ImportCommand(
                file.getBytes(StandardCharsets.UTF_8), "areas.kml", "SE", dryRun, mode, null);
    }

    private int countBatches() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM import_batches WHERE state_code = 'SE'", Integer.class);
        return count == null ? 0 : count;
    }

    private int countAreas() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM beaches WHERE external_id LIKE ?", Integer.class, PREFIX + "%");
        return count == null ? 0 : count;
    }

    private int countSectors() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM beach_sectors WHERE external_id LIKE ?",
                Integer.class, PREFIX + "%");
        return count == null ? 0 : count;
    }

    private UUID areaId(String externalId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM beaches WHERE external_id = ?", UUID.class, externalId);
    }

    private UUID sectorId(String externalId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM beach_sectors WHERE external_id = ?", UUID.class, externalId);
    }

    private boolean isActive(UUID sectorId) {
        Boolean active = jdbcTemplate.queryForObject(
                "SELECT active FROM beach_sectors WHERE id = ?", Boolean.class, sectorId);
        return Boolean.TRUE.equals(active);
    }

    private static String kml(Feature... features) {
        StringBuilder kml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Document>
                """);
        Arrays.stream(features).map(BathingAreaImportIntegrationTests::placemark)
                .forEach(kml::append);
        return kml.append("""
                  </Document>
                </kml>
                """).toString();
    }

    private static String placemark(Feature feature) {
        double minX = feature.minX();
        double minY = feature.minY();
        double maxX = minX + 0.001;
        double maxY = minY + 0.001;
        String estado = feature.estado() == null ? "" : """
                    <SimpleData name="Estado">%s</SimpleData>
                """.formatted(feature.estado());
        String setor = feature.setor() == null ? "-" : feature.setor();
        return """
                    <Placemark>
                      <ExtendedData>
                        <SchemaData schemaUrl="#areas">
                          <SimpleData name="id">%s</SimpleData>
                          <SimpleData name="Praia">%s</SimpleData>
                          <SimpleData name="Setor">%s</SimpleData>
                          <SimpleData name="Municipio">%s</SimpleData>
                %s        </SchemaData>
                      </ExtendedData>
                      <MultiGeometry>
                        <Polygon>
                          <outerBoundaryIs>
                            <LinearRing>
                              <coordinates>%s,%s %s,%s %s,%s %s,%s %s,%s</coordinates>
                            </LinearRing>
                          </outerBoundaryIs>
                        </Polygon>
                      </MultiGeometry>
                    </Placemark>
                """.formatted(
                feature.id(), feature.praia(), setor, feature.municipio(), estado,
                minX, minY, maxX, minY, maxX, maxY, minX, maxY, minX, minY);
    }

    private record Feature(
            String id,
            String praia,
            String setor,
            String municipio,
            String estado,
            double minX,
            double minY) {
    }

    private static Feature feature(
            String id, String praia, String setor, String municipio,
            String estado, double minX, double minY) {
        return new Feature(id, praia, setor, municipio, estado, minX, minY);
    }
}
