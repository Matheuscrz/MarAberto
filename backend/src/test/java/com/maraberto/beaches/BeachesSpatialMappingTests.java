package com.maraberto.beaches;

import static org.assertj.core.api.Assertions.assertThat;

import com.maraberto.beaches.domain.Beach;
import com.maraberto.beaches.domain.CriticalZone;
import com.maraberto.beaches.domain.LifeguardPost;
import com.maraberto.beaches.domain.MobileBase;
import com.maraberto.beaches.domain.RiskPersistence;
import com.maraberto.beaches.domain.RiskType;
import com.maraberto.support.PostgisIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BeachesSpatialMappingTests extends PostgisIntegrationTest {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayEnablesPostgisAndCreatesSpatialIndexes() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'postgis'", Integer.class))
                .isEqualTo(1);

        assertThat(tables()).contains(
                "beaches", "beach_sectors", "import_batches", "lifeguard_posts",
                "mobile_bases", "critical_zones");

        assertThat(jdbcTemplate.queryForList("""
                SELECT indexname
                  FROM pg_indexes
                 WHERE schemaname = 'public'
                   AND indexdef LIKE '%USING gist%'
                """, String.class))
                .containsExactlyInAnyOrder(
                        "idx_beaches_boundary",
                        "idx_lifeguard_posts_location",
                        "idx_mobile_bases_location",
                        "idx_critical_zones_geometry",
                        "idx_emergency_calls_location",
                        "idx_sector_geom",
                        "idx_beach_map_features_geom");

        assertThat(jdbcTemplate.queryForList("""
                SELECT conname
                  FROM pg_constraint
                 WHERE conrelid = 'critical_zones'::regclass
                   AND contype = 'c'
                """, String.class))
                .containsExactlyInAnyOrder(
                        "ck_critical_zones_persistence",
                        "ck_critical_zones_validity",
                        "ck_critical_zones_permanent_no_expiry",
                        "ck_critical_zones_semi_permanent_expiry");
    }

    @Test
    void persistsEntitiesWithExpectedGeometryTypesAndSrid() {
        Beach beach = new Beach();
        beach.setName("Praia de Teste");
        beach.setMunicipality("Aracaju");
        beach.setStateCode("SE");
        beach.setBoundary(multiPolygon(-35.700, -10.000, 0.010));
        entityManager.persist(beach);

        LifeguardPost post = new LifeguardPost();
        post.setBeach(beach);
        post.setName("Posto 1");
        post.setLocation(point(-35.695, -10.005));
        entityManager.persist(post);

        MobileBase mobileBase = new MobileBase();
        mobileBase.setName("Base Móvel 1");
        mobileBase.setLocation(point(-35.690, -10.010));
        entityManager.persist(mobileBase);

        CriticalZone zone = new CriticalZone();
        zone.setBeach(beach);
        zone.setName("Corrente de retorno junto ao molhe");
        zone.setRiskType(RiskType.RIP_CURRENT);
        zone.setPersistence(RiskPersistence.PERMANENT);
        zone.setDescription("Aviso permanente de perigo estrutural");
        zone.setGeometry(polygon(-35.699, -10.004, 0.002));
        entityManager.persist(zone);

        Instant now = Instant.now();
        CriticalZone topographicZone = new CriticalZone();
        topographicZone.setBeach(beach);
        topographicZone.setName("Canal entre bancos de areia");
        topographicZone.setRiskType(RiskType.RIP_CURRENT);
        topographicZone.setPersistence(RiskPersistence.SEMI_PERMANENT);
        topographicZone.setDescription("Corrente topográfica, revalidar após ressaca");
        topographicZone.setGeometry(polygon(-35.696, -10.006, 0.002));
        topographicZone.setValidFrom(now.minus(1, ChronoUnit.DAYS));
        topographicZone.setValidUntil(now.plus(30, ChronoUnit.DAYS));
        topographicZone.setLastConfirmedAt(now);
        entityManager.persist(topographicZone);

        entityManager.flush();
        entityManager.clear();

        Beach loadedBeach = entityManager.find(Beach.class, beach.getId());
        assertThat(loadedBeach.getBoundary().getSRID()).isEqualTo(4326);
        assertThat(loadedBeach.getBoundary().getGeometryType()).isEqualTo("MultiPolygon");

        LifeguardPost loadedPost = entityManager.find(LifeguardPost.class, post.getId());
        assertThat(loadedPost.getLocation().getSRID()).isEqualTo(4326);
        assertThat(loadedPost.getLocation().getX()).isEqualTo(-35.695);
        assertThat(loadedPost.getLocation().getY()).isEqualTo(-10.005);

        MobileBase loadedBase = entityManager.find(MobileBase.class, mobileBase.getId());
        assertThat(loadedBase.getLocation().getX()).isEqualTo(-35.690);

        CriticalZone loadedZone = entityManager.find(CriticalZone.class, zone.getId());
        assertThat(loadedZone.getRiskType()).isEqualTo(RiskType.RIP_CURRENT);
        assertThat(loadedZone.getPersistence()).isEqualTo(RiskPersistence.PERMANENT);
        assertThat(loadedZone.getValidUntil()).isNull();
        assertThat(loadedZone.getGeometry().getSRID()).isEqualTo(4326);

        CriticalZone loadedTopographicZone =
                entityManager.find(CriticalZone.class, topographicZone.getId());
        assertThat(loadedTopographicZone.getPersistence()).isEqualTo(RiskPersistence.SEMI_PERMANENT);
        assertThat(loadedTopographicZone.getValidUntil()).isAfter(loadedTopographicZone.getValidFrom());
        assertThat(loadedTopographicZone.getLastConfirmedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT persistence FROM critical_zones WHERE id = ?",
                String.class, topographicZone.getId()))
                .isEqualTo("SEMI_PERMANENT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ST_SRID(boundary) FROM beaches WHERE id = ?", Integer.class, beach.getId()))
                .isEqualTo(4326);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ST_GeometryType(boundary) FROM beaches WHERE id = ?", String.class, beach.getId()))
                .isEqualTo("ST_MultiPolygon");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ST_SRID(location::geometry) FROM lifeguard_posts WHERE id = ?",
                Integer.class, post.getId()))
                .isEqualTo(4326);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ST_SRID(location::geometry) FROM mobile_bases WHERE id = ?",
                Integer.class, mobileBase.getId()))
                .isEqualTo(4326);
    }

    @Test
    void evaluatesTemporalVisibility() {
        Instant now = Instant.now();

        CriticalZone permanent = new CriticalZone();
        permanent.setPersistence(RiskPersistence.PERMANENT);
        permanent.setValidFrom(now.minus(1, ChronoUnit.DAYS));
        assertThat(permanent.isVisibleAt(now)).isTrue();

        CriticalZone expired = new CriticalZone();
        expired.setPersistence(RiskPersistence.SEMI_PERMANENT);
        expired.setValidFrom(now.minus(10, ChronoUnit.DAYS));
        expired.setValidUntil(now.minus(1, ChronoUnit.DAYS));
        assertThat(expired.isVisibleAt(now)).isFalse();

        CriticalZone future = new CriticalZone();
        future.setPersistence(RiskPersistence.SEMI_PERMANENT);
        future.setValidFrom(now.plus(1, ChronoUnit.DAYS));
        future.setValidUntil(now.plus(10, ChronoUnit.DAYS));
        assertThat(future.isVisibleAt(now)).isFalse();

        CriticalZone deactivated = new CriticalZone();
        deactivated.setPersistence(RiskPersistence.PERMANENT);
        deactivated.setValidFrom(now.minus(1, ChronoUnit.DAYS));
        deactivated.setActive(false);
        assertThat(deactivated.isVisibleAt(now)).isFalse();
    }

    private List<String> tables() {
        return jdbcTemplate.queryForList("""
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                """, String.class);
    }

    private static MultiPolygon multiPolygon(double minX, double minY, double size) {
        return GEOMETRY_FACTORY.createMultiPolygon(new Polygon[] {
                polygon(minX, minY, size)
        });
    }

    private static Polygon polygon(double minX, double minY, double size) {
        return GEOMETRY_FACTORY.createPolygon(new Coordinate[] {
                new Coordinate(minX, minY),
                new Coordinate(minX + size, minY),
                new Coordinate(minX + size, minY + size),
                new Coordinate(minX, minY + size),
                new Coordinate(minX, minY)
        });
    }

    private static Point point(double x, double y) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(x, y));
    }
}
