package com.maraberto.emergency.application;

import com.maraberto.emergency.api.SosCallCloser;
import com.maraberto.emergency.api.SosCallOutcome;
import java.sql.Types;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encerra o chamado do SOS quando a ocorrência operacional é encerrada.
 *
 * <p>A transição é um {@code UPDATE ... WHERE status IN (...)} (compare-and-set
 * por estado): se o chamado já foi resolvido, nada muda. O console deixa de
 * listá-lo em "Em atendimento por você" porque a consulta filtra por esses
 * mesmos estados.
 */
@Service
public class SosCallCloserService implements SosCallCloser {

    private static final String CLOSE_SQL = """
            UPDATE emergency_calls
            SET status = :status,
                resolved_at = NOW(),
                resolved_by = :actorId,
                next_escalation_at = NULL,
                updated_at = NOW(),
                version = version + 1
            WHERE id = :callId
              AND status IN ('ACKNOWLEDGED', 'EN_ROUTE', 'ON_SCENE')
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SosCallCloserService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void closeFromOccurrence(UUID callId, SosCallOutcome outcome, UUID actorId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("callId", callId)
                .addValue("actorId", actorId, Types.OTHER)
                .addValue("status", outcome.name(), Types.VARCHAR);
        jdbc.update(CLOSE_SQL, params);
    }
}
