package com.maraberto.beaches;

import static org.assertj.core.api.Assertions.assertThat;

import com.maraberto.beaches.domain.Beach;
import com.maraberto.beaches.domain.CriticalZone;
import com.maraberto.beaches.domain.LifeguardPost;
import com.maraberto.beaches.domain.MobileBase;
import com.maraberto.beaches.domain.RiskType;
import com.maraberto.support.PostgisIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
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

        assertThat(tables()).contains("beaches", "lifeguard_posts", "mobile_bases", "critical_zones");

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
                        "idx_critical_zones_geometry");
    }

    @Test
    void persistsEntitiesWithExpectedGeometryTypesAndSrid() {
        Beach beach = new Beach();
        beach.setName("Praia de Teste");
        beach.setMunicipality("Aracaju");
        beach.setStateCode("SE");
        beach.setBoundary(polygon(-35.700, -10.000, 0.010));
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
        zone.setName("Corrente de retorno");
        zone.setRiskType(RiskType.RIP_CURRENT);
        zone.setDescription("Aviso permanente de perigo estrutural");
        zone.setGeometry(polygon(-35.699, -10.004, 0.002));
        entityManager.persist(zone);

        entityManager.flush();
        entityManager.clear();

        Beach loadedBeach = entityManager.find(Beach.class, beach.getId());
        assertThat(loadedBeach.getBoundary().getSRID()).isEqualTo(4326);
        assertThat(loadedBeach.getBoundary().getGeometryType()).isEqualTo("Polygon");

        LifeguardPost loadedPost = entityManager.find(LifeguardPost.class, post.getId());
        assertThat(loadedPost.getLocation().getSRID()).isEqualTo(4326);
        assertThat(loadedPost.getLocation().getX()).isEqualTo(-35.695);
        assertThat(loadedPost.getLocation().getY()).isEqualTo(-10.005);

        MobileBase loadedBase = entityManager.find(MobileBase.class, mobileBase.getId());
        assertThat(loadedBase.getLocation().getX()).isEqualTo(-35.690);

        CriticalZone loadedZone = entityManager.find(CriticalZone.class, zone.getId());
        assertThat(loadedZone.getRiskType()).isEqualTo(RiskType.RIP_CURRENT);
        assertThat(loadedZone.getGeometry().getSRID()).isEqualTo(4326);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT ST_SRID(boundary) FROM beaches WHERE id = ?", Integer.class, beach.getId()))
                .isEqualTo(4326);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ST_GeometryType(boundary) FROM beaches WHERE id = ?", String.class, beach.getId()))
                .isEqualTo("ST_Polygon");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ST_SRID(location::geometry) FROM lifeguard_posts WHERE id = ?",
                Integer.class, post.getId()))
                .isEqualTo(4326);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ST_SRID(location::geometry) FROM mobile_bases WHERE id = ?",
                Integer.class, mobileBase.getId()))
                .isEqualTo(4326);
    }

    private List<String> tables() {
        return jdbcTemplate.queryForList("""
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                """, String.class);
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
