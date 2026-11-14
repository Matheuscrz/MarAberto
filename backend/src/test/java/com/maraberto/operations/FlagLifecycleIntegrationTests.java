package com.maraberto.operations;

import static com.maraberto.support.KmlFixtures.feature;
import static com.maraberto.support.KmlFixtures.kml;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maraberto.beaches.application.BathingAreaImportService;
import com.maraberto.beaches.application.BathingAreaImportService.ImportCommand;
import com.maraberto.beaches.application.ImportMode;
import com.maraberto.identity.api.dto.RegisterOperationalUserCommand;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.identity.domain.Role;
import com.maraberto.operations.api.FlagResponse;
import com.maraberto.operations.api.LaunchFlagRequest;
import com.maraberto.operations.api.PublicFlag;
import com.maraberto.operations.application.FlagService;
import com.maraberto.operations.domain.FlagColor;
import com.maraberto.operations.domain.FlagNotFoundException;
import com.maraberto.support.PostgisIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Ciclo de vida da sinalização (RF12/RN01).
 *
 * <p>Confere o que fica no banco e o que sai na lista pública: lançamento
 * append-only, encerramento por escopo, validade aplicada na consulta e pin no
 * setor quando o lançamento é de setor.
 */
@SpringBootTest
class FlagLifecycleIntegrationTests extends PostgisIntegrationTest {

    private static final String PREFIX = "se-areas-banho:";

    @Autowired
    private BathingAreaImportService importService;

    @Autowired
    private FlagService flagService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID operatorId;

    @BeforeEach
    @AfterEach
    void cleanFlagsAndMap() {
        jdbcTemplate.update("DELETE FROM flag_updates");
        jdbcTemplate.update("DELETE FROM beach_sectors WHERE external_id LIKE ?", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM import_batches WHERE state_code = 'SE'");
        jdbcTemplate.update("DELETE FROM beaches WHERE external_id LIKE ?", PREFIX + "%");
    }

    @BeforeEach
    void registerOperator() {
        String suffix = UUID.randomUUID().toString();
        operatorId = authenticationService.registerOperational(
                new RegisterOperationalUserCommand(
                        "Operador " + suffix.substring(0, 8),
                        "T-" + suffix.substring(0, 8),
                        "sinaliza-" + suffix + "@teste.dev",
                        validCpf(suffix),
                        "demo1234",
                        Role.LIFEGUARD));
    }

    @Test
    void aSectorFlagClosesOnlyTheSectorAndLeavesTheBeachFlagAlone() {
        UUID beachId = importBeachWithTwoSectors();
        UUID north = sectorId(beachId, "Setor Norte");
        UUID south = sectorId(beachId, "Setor Sul");

        flagService.launch(operatorId, launch(beachId, null, FlagColor.RED, null));
        flagService.launch(operatorId, launch(beachId, north, FlagColor.YELLOW, null));

        List<PublicFlag> flags = flagService.publicFlags(null, false);
        assertThat(flags).hasSize(2);
        assertThat(flags).filteredOn(flag -> flag.sectorId() == null)
                .singleElement()
                .extracting(PublicFlag::flag).isEqualTo(FlagColor.RED);
        assertThat(flags).filteredOn(flag -> north.equals(flag.sectorId()))
                .singleElement()
                .extracting(PublicFlag::sectorLabel).isEqualTo("Setor Norte");

        // Novo lançamento no Norte fecha só o anterior do Norte.
        flagService.launch(operatorId, launch(beachId, north, FlagColor.GREEN, null));

        flags = flagService.publicFlags(null, false);
        assertThat(flags).hasSize(2);
        assertThat(flags).filteredOn(flag -> north.equals(flag.sectorId()))
                .singleElement()
                .extracting(PublicFlag::flag).isEqualTo(FlagColor.GREEN);
        assertThat(flags).filteredOn(flag -> south.equals(flag.sectorId())).isEmpty();

        assertThat(openFlagsInScope(beachId, north)).isEqualTo(1);
        assertThat(closedFlagsInScope(beachId, north)).isEqualTo(1);
    }

    @Test
    void theDefaultValidityIsTwelveHours() {
        UUID beachId = importBeachWithTwoSectors();

        FlagResponse response = flagService.launch(
                operatorId, launch(beachId, null, FlagColor.YELLOW, null)).flag();

        assertValidity(response, Duration.ofHours(12));
    }

    @Test
    void theRequestCanShortenTheValidityButNeverPassTwentyFourHours() {
        UUID beachId = importBeachWithTwoSectors();

        FlagResponse shortOne = flagService.launch(
                operatorId, launch(beachId, null, FlagColor.YELLOW, 2.0)).flag();
        assertValidity(shortOne, Duration.ofHours(2));

        FlagResponse capped = flagService.launch(
                operatorId, launch(beachId, null, FlagColor.YELLOW, 48.0)).flag();
        assertValidity(capped, Duration.ofHours(24));
    }

    @Test
    void anExpiredFlagLeavesTheMapWithoutLeavingTheHistory() {
        UUID beachId = importBeachWithTwoSectors();
        FlagResponse launched = flagService.launch(
                operatorId, launch(beachId, null, FlagColor.RED, null)).flag();

        // Venceu: some da lista pública, mas a linha continua no histórico.
        jdbcTemplate.update("""
                UPDATE flag_updates
                   SET created_at = NOW() - INTERVAL '2 hours',
                       valid_until = NOW() - INTERVAL '1 minute'
                 WHERE id = ?
                """, launched.id());

        assertThat(flagService.publicFlags(null, false)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flag_updates", Integer.class)).isEqualTo(1);
    }

    @Test
    void theBeachDetailReceivesTheLastFlagOfEachScopeEvenWhenItExpired() {
        UUID beachId = importBeachWithTwoSectors();
        UUID north = sectorId(beachId, "Setor Norte");
        flagService.launch(operatorId, launch(beachId, null, FlagColor.RED, null));
        flagService.launch(operatorId, launch(beachId, north, FlagColor.YELLOW, null));

        // Os dois vencem: o mapa fica vazio, a ficha ainda sabe a última cor.
        jdbcTemplate.update("""
                UPDATE flag_updates
                   SET created_at = created_at - INTERVAL '2 days',
                       valid_until = NOW() - INTERVAL '1 day'
                 WHERE beach_id = ?
                """, beachId);

        assertThat(flagService.publicFlags(beachId, false)).isEmpty();

        List<PublicFlag> latest = flagService.publicFlags(beachId, true);
        assertThat(latest).hasSize(2);
        assertThat(latest).allSatisfy(flag -> assertThat(flag.active()).isFalse());
        assertThat(latest).filteredOn(flag -> flag.sectorId() == null)
                .singleElement()
                .extracting(PublicFlag::flag).isEqualTo(FlagColor.RED);
        assertThat(latest).filteredOn(flag -> north.equals(flag.sectorId()))
                .singleElement()
                .extracting(PublicFlag::sectorLabel).isEqualTo("Setor Norte");
    }

    @Test
    void theLatestFlagOfAScopeIsTheNewestOneEvenAfterThePreviousClosed() {
        UUID beachId = importBeachWithTwoSectors();
        flagService.launch(operatorId, launch(beachId, null, FlagColor.RED, null));
        flagService.launch(operatorId, launch(beachId, null, FlagColor.GREEN, null));

        List<PublicFlag> latest = flagService.publicFlags(beachId, true);

        assertThat(latest).hasSize(1);
        assertThat(latest.getFirst().flag()).isEqualTo(FlagColor.GREEN);
        assertThat(latest.getFirst().active()).isTrue();
    }

    @Test
    void theExpiredHistoryRequiresABeach() {
        assertThatThrownBy(() -> flagService.publicFlags(null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closingAFlagRemovesItFromTheMapAndCannotBeRepeated() {
        UUID beachId = importBeachWithTwoSectors();
        FlagResponse launched = flagService.launch(
                operatorId, launch(beachId, null, FlagColor.RED, null)).flag();

        FlagResponse closed = flagService.close(operatorId, launched.id());

        assertThat(closed.closedAt()).isNotNull();
        assertThat(flagService.publicFlags(null, false)).isEmpty();
        assertThatThrownBy(() -> flagService.close(operatorId, launched.id()))
                .isInstanceOf(FlagNotFoundException.class);
    }

    @Test
    void aFlagOfADeactivatedSectorIsNotPublished() {
        UUID beachId = importBeachWithTwoSectors();
        UUID north = sectorId(beachId, "Setor Norte");
        flagService.launch(operatorId, launch(beachId, north, FlagColor.YELLOW, null));

        jdbcTemplate.update("UPDATE beach_sectors SET active = FALSE WHERE id = ?", north);

        assertThat(flagService.publicFlags(null, false)).isEmpty();
    }

    @Test
    void theFlagOfAWholeBeachFallsInsideTheArea() {
        UUID beachId = importBeachWithTwoSectors();
        flagService.launch(operatorId, launch(beachId, null, FlagColor.GREEN, null));

        PublicFlag flag = flagService.publicFlags(null, false).getFirst();

        // O pin de praia inteira é um ponto interno da área, não o centroide
        // geométrico (que pode cair fora de um polígono côncavo).
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*)
                          FROM beaches b
                         WHERE b.id = ?
                           AND ST_Contains(
                                   b.boundary,
                                   ST_SetSRID(ST_MakePoint(?, ?), 4326))
                        """, Integer.class, beachId, flag.longitude(), flag.latitude()))
                .isEqualTo(1);
        assertThat(flag.beachName()).isEqualTo("Praia Perto");
        assertThat(flag.sectorLabel()).isNull();
    }

    @Test
    void launchingForAnInactiveAreaOrForeignSectorIsRejected() {
        UUID beachId = importBeachWithTwoSectors();
        UUID north = sectorId(beachId, "Setor Norte");

        // Setor de outra área não pode ser sinalizado por esta área.
        UUID otherBeach = importBeach("9", "Outra Praia", "Outro Setor", -35.500, -10.500);
        assertThatThrownBy(() -> flagService.launch(
                operatorId, launch(otherBeach, north, FlagColor.RED, null)))
                .isInstanceOf(IllegalArgumentException.class);

        jdbcTemplate.update("UPDATE beaches SET active = FALSE WHERE id = ?", beachId);
        assertThatThrownBy(() -> flagService.launch(
                operatorId, launch(beachId, null, FlagColor.RED, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private LaunchFlagRequest launch(
            UUID beachId, UUID sectorId, FlagColor flag, Double validityHours) {
        return new LaunchFlagRequest(
                beachId, sectorId, flag, "Mar agitado", validityHours,
                null, null, null, null);
    }

    private static void assertValidity(FlagResponse response, Duration expected) {
        assertThat(Duration.between(response.createdAt(), response.validUntil()))
                .isBetween(expected.minusSeconds(2), expected.plusSeconds(2));
    }

    private UUID importBeachWithTwoSectors() {
        String file = kml(
                feature("1", "Praia Perto", "Setor Norte", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia Perto", "Setor Sul", "Aracaju", null, -35.050, -10.050));
        importService.importKml(new ImportCommand(
                file.getBytes(StandardCharsets.UTF_8), "areas.kml", "SE", false,
                ImportMode.UPSERT, null));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM beaches WHERE external_id = ?", UUID.class, PREFIX + "area:1");
    }

    private UUID importBeach(
            String id, String praia, String setor, double minX, double minY) {
        String file = kml(feature(id, praia, setor, "Aracaju", null, minX, minY));
        importService.importKml(new ImportCommand(
                file.getBytes(StandardCharsets.UTF_8), "area-" + id + ".kml", "SE", false,
                ImportMode.UPSERT, null));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM beaches WHERE external_id = ?", UUID.class,
                PREFIX + "area:" + id);
    }

    private UUID sectorId(UUID beachId, String name) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM beach_sectors WHERE beach_id = ? AND name = ?",
                UUID.class, beachId, name);
    }

    private int openFlagsInScope(UUID beachId, UUID sectorId) {
        return jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM flag_updates
                         WHERE beach_id = ? AND sector_id = ? AND closed_at IS NULL
                        """, Integer.class, beachId, sectorId);
    }

    private int closedFlagsInScope(UUID beachId, UUID sectorId) {
        return jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM flag_updates
                         WHERE beach_id = ? AND sector_id = ? AND closed_at IS NOT NULL
                        """, Integer.class, beachId, sectorId);
    }

    private static String validCpf(String seed) {
        int[] digits = new int[11];
        for (int index = 0; index < 9; index++) {
            digits[index] = ThreadLocalRandom.current().nextInt(10);
        }
        digits[9] = checkDigit(digits, 9, 10);
        digits[10] = checkDigit(digits, 10, 11);
        StringBuilder builder = new StringBuilder(11);
        for (int digit : digits) {
            builder.append(digit);
        }
        return builder.toString();
    }

    private static int checkDigit(int[] digits, int length, int weight) {
        int sum = 0;
        for (int index = 0; index < length; index++) {
            sum += digits[index] * (weight - index);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}
