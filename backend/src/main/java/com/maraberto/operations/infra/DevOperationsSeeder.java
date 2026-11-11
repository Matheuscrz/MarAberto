package com.maraberto.operations.infra;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Posto fixo e base móvel de desenvolvimento para exercitar o plantão e o
 * despacho do SOS sem depender do importador de mapeamento.
 *
 * <p>Só roda com {@code maraberto.operations.dev-seed.enabled=true} e
 * <strong>falha ao subir</strong> no perfil {@code prod}/{@code production}:
 * estrutura operacional é dado do órgão, não do repositório. É idempotente —
 * se já existe posto ou base, não insere nada para não misturar dado real.
 */
@Component
@ConditionalOnProperty(
        prefix = "maraberto.operations.dev-seed",
        name = "enabled",
        havingValue = "true")
public class DevOperationsSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevOperationsSeeder.class);

    private static final String BEACH_BOUNDARY = "POLYGON(("
            + "-35.7160 -9.6700, -35.7020 -9.6700, "
            + "-35.7020 -9.6620, -35.7160 -9.6620, "
            + "-35.7160 -9.6700))";

    private final JdbcTemplate jdbc;
    private final Environment environment;

    public DevOperationsSeeder(JdbcTemplate jdbc, Environment environment) {
        this.jdbc = jdbc;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException(
                    "maraberto.operations.dev-seed.enabled não pode ser usado no perfil de produção");
        }

        Integer existing = jdbc.queryForObject(
                "SELECT (SELECT count(*) FROM lifeguard_posts) "
                        + "+ (SELECT count(*) FROM mobile_bases)",
                Integer.class);
        if (existing != null && existing > 0) {
            return;
        }

        UUID beachId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO beaches (id, name, municipality, state_code, boundary)
                        VALUES (?, 'Praia de Pajuçara', 'Maceió', 'AL',
                                ST_GeomFromText(?, 4326))
                        """,
                beachId, BEACH_BOUNDARY);

        jdbc.update("""
                        INSERT INTO lifeguard_posts
                            (id, beach_id, name, location, active, coverage_radius_m)
                        VALUES (?, ?, 'Posto Pajuçara 01',
                                CAST(ST_SetSRID(ST_MakePoint(-35.7089, -9.6658), 4326)
                                    AS geography),
                                TRUE, 2000)
                        """,
                UUID.randomUUID(), beachId);

        jdbc.update("""
                        INSERT INTO mobile_bases
                            (id, name, location, active, coverage_radius_m)
                        VALUES (?, 'Base Móvel Orla',
                                CAST(ST_SetSRID(ST_MakePoint(-35.7045, -9.6645), 4326)
                                    AS geography),
                                TRUE, 3000)
                        """,
                UUID.randomUUID());

        log.warn("Posto e base de desenvolvimento criados. "
                + "Desligue maraberto.operations.dev-seed.enabled fora do ambiente local.");
    }
}
