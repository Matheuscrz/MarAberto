package com.maraberto.beaches.infra;

import com.maraberto.beaches.api.BathingAreaOption;
import com.maraberto.beaches.domain.BeachKind;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lista de áreas ativas para os formulários da coordenação.
 *
 * <p>Lê a tabela base, não a projeção do mapa: aqui interessa o cadastro da
 * área (nome, município, setores ativos), não a geometria simplificada que o
 * banhista baixa.
 */
@Repository
public class BathingAreaCatalogRepository {

    private final JdbcTemplate jdbc;

    public BathingAreaCatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<BathingAreaOption> findActive() {
        return jdbc.query("""
                        SELECT b.id,
                               b.name,
                               b.kind,
                               b.municipality,
                               b.state_code,
                               (SELECT count(*)
                                  FROM beach_sectors s
                                 WHERE s.beach_id = b.id
                                   AND s.active) AS sector_count
                          FROM beaches b
                         WHERE b.active
                         ORDER BY b.municipality, b.name, b.id
                        """,
                (rs, rowNum) -> new BathingAreaOption(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        BeachKind.valueOf(rs.getString("kind")),
                        rs.getString("municipality"),
                        rs.getString("state_code"),
                        rs.getInt("sector_count")));
    }
}
