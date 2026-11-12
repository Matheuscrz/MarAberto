package com.maraberto.operations.infra;

import com.maraberto.operations.api.EligibleResponder;
import com.maraberto.operations.api.EligibleRespondersPort;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Seleção espacial dos plantões elegíveis para um SOS (ADR-017/ADR-018).
 *
 * <p>A consulta roda dentro da transação do chamado e devolve <strong>todos</strong>
 * os plantões abertos cujo ponto está dentro do raio de cobertura — não uma
 * amostra dos mais próximos. O limite de segurança existe para uma
 * configuração absurda de raio, não para escolher "os 3 primeiros".
 *
 * <p><strong>Ponto da decisão:</strong> a última posição conhecida do plantão
 * ({@code shift_sessions.last_position}). Sem posição reportada, vale a posição
 * da estrutura (posto fixo/base móvel) — nunca o contrário: medir pela
 * estrutura quando o guarda-vidas está em outro lugar foi o que fez um plantão
 * a 200 km do chamado aparecer como "mais próximo". O raio continua sendo o da
 * estrutura ({@code coverage_radius_m}).
 *
 * <p><strong>Acesso:</strong> o conjunto é o de plantões abertos (índices
 * parciais da {@code V8}) e a estrutura entra por chave primária. Filtrar pela
 * posição do plantão não usa o GiST da estrutura; para centenas de plantões
 * abertos o custo é menor do que a alternativa de decidir pelo posto e errar a
 * distância de quem está em campo.
 *
 * <p>Sem ninguém no raio, o fallback é o vizinho mais próximo (KNN puro) para
 * que o chamado nunca fique sem destinatário; sem nenhum plantão aberto a lista
 * fica vazia e o chamador aciona a coordenação (RN03).
 */
@Repository
public class JdbcEligibleRespondersRepository implements EligibleRespondersPort {

    private static final String WITHIN_RADIUS_SQL = """
            WITH sos AS (
                SELECT CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography) AS point
            )
            SELECT s.user_id, s.id AS shift_session_id, s.post_id, s.mobile_base_id,
                   -- A distância é do plantão: a posição reportada vale mais que a
                   -- estrutura, que pode estar a centenas de km de onde a pessoa está.
                   ST_Distance(COALESCE(s.last_position, p.location), sos.point) AS distance_m,
                   -- Sem posição reportada a distância vem do cadastro do posto, que
                   -- pode estar longe de onde o guarda-vidas está: isso é sinal, não
                   -- detalhe. `stale` cobre "nunca reportou" e "reportou há muito".
                   (s.last_position_at IS NULL OR s.last_position_at < :staleBefore)
                       AS position_stale
            FROM lifeguard_posts p
            JOIN sos ON TRUE
            JOIN shift_sessions s ON s.post_id = p.id AND s.ended_at IS NULL
            JOIN operational_users u ON u.id = s.user_id AND u.active
            WHERE p.active
              -- Lease do plantão: app fechado à força não recebe chamado.
              AND s.last_seen_at >= :aliveSince
              -- O prefiltro do GiST não pode entrar aqui: um posto distante com o
              -- operador em campo perto do chamado ficaria de fora. O conjunto
              -- varrido é o de plantões abertos (índice parcial da V8).
              -- `LEAST` aplica o teto da instalação: um raio cadastrado errado
              -- (ou muito generoso) não pode chamar quem está longe demais.
              AND ST_DWithin(COALESCE(s.last_position, p.location), sos.point,
                             LEAST(COALESCE(p.coverage_radius_m, :defaultRadiusMeters),
                                   :maxRadiusMeters))
            UNION ALL
            SELECT s.user_id, s.id, s.post_id, s.mobile_base_id,
                   ST_Distance(COALESCE(s.last_position, b.location), sos.point) AS distance_m,
                   (s.last_position_at IS NULL OR s.last_position_at < :staleBefore) AS position_stale
            FROM mobile_bases b
            JOIN sos ON TRUE
            JOIN shift_sessions s ON s.mobile_base_id = b.id AND s.ended_at IS NULL
            JOIN operational_users u ON u.id = s.user_id AND u.active
            WHERE b.active
              AND s.last_seen_at >= :aliveSince
              AND ST_DWithin(COALESCE(s.last_position, b.location), sos.point,
                             LEAST(COALESCE(b.coverage_radius_m, :defaultRadiusMeters),
                                   :maxRadiusMeters))
            ORDER BY distance_m
            LIMIT :maxResults
            """;

    private static final String RING_SQL = """
            WITH sos AS (
                SELECT CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography) AS point
            )
            SELECT s.user_id, s.id AS shift_session_id, s.post_id, s.mobile_base_id,
                   ST_Distance(COALESCE(s.last_position, p.location), sos.point) AS distance_m,
                   (s.last_position_at IS NULL OR s.last_position_at < :staleBefore)
                       AS position_stale
            FROM lifeguard_posts p
            JOIN sos ON TRUE
            JOIN shift_sessions s ON s.post_id = p.id AND s.ended_at IS NULL
            JOIN operational_users u ON u.id = s.user_id AND u.active
            WHERE p.active
              AND s.last_seen_at >= :aliveSince
              -- Anel absoluto: o raio cadastrado do posto não entra aqui. Ele já
              -- foi usado no round 1; a escada existe justamente para ir além.
              AND ST_DWithin(COALESCE(s.last_position, p.location), sos.point, :ringMeters)
              AND NOT (s.user_id = ANY (CAST(:excluded AS uuid[])))
            UNION ALL
            SELECT s.user_id, s.id AS shift_session_id, s.post_id, s.mobile_base_id,
                   ST_Distance(COALESCE(s.last_position, b.location), sos.point) AS distance_m,
                   (s.last_position_at IS NULL OR s.last_position_at < :staleBefore)
                       AS position_stale
            FROM mobile_bases b
            JOIN sos ON TRUE
            JOIN shift_sessions s ON s.mobile_base_id = b.id AND s.ended_at IS NULL
            JOIN operational_users u ON u.id = s.user_id AND u.active
            WHERE b.active
              AND s.last_seen_at >= :aliveSince
              AND ST_DWithin(COALESCE(s.last_position, b.location), sos.point, :ringMeters)
              AND NOT (s.user_id = ANY (CAST(:excluded AS uuid[])))
            ORDER BY distance_m
            LIMIT :maxResults
            """;

    private static final String NEAREST_SQL = """
            WITH sos AS (
                SELECT CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography) AS point
            )
            SELECT s.user_id, s.id AS shift_session_id, s.post_id, s.mobile_base_id,
                   ST_Distance(COALESCE(s.last_position, p.location), sos.point) AS distance_m,
                   (s.last_position_at IS NULL OR s.last_position_at < :staleBefore)
                       AS position_stale
            FROM shift_sessions s
            JOIN sos ON TRUE
            JOIN lifeguard_posts p ON p.id = s.post_id AND p.active
            JOIN operational_users u ON u.id = s.user_id AND u.active
            WHERE s.ended_at IS NULL
              AND s.last_seen_at >= :aliveSince
              AND s.post_id IS NOT NULL
              -- Fora do teto não é "vizinho": a coordenação assume o chamado.
              AND ST_DWithin(COALESCE(s.last_position, p.location), sos.point, :maxRadiusMeters)
            UNION ALL
            SELECT s.user_id, s.id, s.post_id, s.mobile_base_id,
                   ST_Distance(COALESCE(s.last_position, b.location), sos.point) AS distance_m,
                   (s.last_position_at IS NULL OR s.last_position_at < :staleBefore) AS position_stale
            FROM shift_sessions s
            JOIN sos ON TRUE
            JOIN mobile_bases b ON b.id = s.mobile_base_id AND b.active
            JOIN operational_users u ON u.id = s.user_id AND u.active
            WHERE s.ended_at IS NULL
              AND s.last_seen_at >= :aliveSince
              AND s.mobile_base_id IS NOT NULL
              AND ST_DWithin(COALESCE(s.last_position, b.location), sos.point, :maxRadiusMeters)
            ORDER BY distance_m
            LIMIT :fallbackResults
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final int defaultRadiusMeters;
    private final int maxRecipients;
    private final int fallbackRecipients;
    private final int maxRadiusMeters;
    private final Duration positionStaleAfter;
    private final Duration shiftIdleTimeout;

    public JdbcEligibleRespondersRepository(
            NamedParameterJdbcTemplate jdbc,
            @Value("${maraberto.dispatch.default-coverage-radius-m:2000}") int defaultRadiusMeters,
            @Value("${maraberto.dispatch.max-recipients:50}") int maxRecipients,
            @Value("${maraberto.dispatch.fallback-recipients:3}") int fallbackRecipients,
            @Value("${maraberto.dispatch.max-radius-m:5000}") int maxRadiusMeters,
            @Value("${maraberto.dispatch.position-stale-after:PT30M}") Duration positionStaleAfter,
            @Value("${maraberto.dispatch.shift-idle-timeout:PT15M}") Duration shiftIdleTimeout) {
        this.jdbc = jdbc;
        this.defaultRadiusMeters = defaultRadiusMeters;
        this.maxRecipients = maxRecipients;
        this.fallbackRecipients = fallbackRecipients;
        this.maxRadiusMeters = maxRadiusMeters;
        this.positionStaleAfter = positionStaleAfter;
        this.shiftIdleTimeout = shiftIdleTimeout;
    }

    @Override
    public List<EligibleResponder> findEligible(double latitude, double longitude) {
        if (maxRecipients <= 0) {
            return List.of();
        }
        MapSqlParameterSource parameters = commonParameters(latitude, longitude)
                .addValue("maxResults", maxRecipients);

        List<EligibleResponder> responders = jdbc.query(WITHIN_RADIUS_SQL, parameters, this::mapRow);
        if (!responders.isEmpty()) {
            return responders;
        }
        if (fallbackRecipients <= 0) {
            return List.of();
        }
        return jdbc.query(
                NEAREST_SQL,
                commonParameters(latitude, longitude).addValue("fallbackResults", fallbackRecipients),
                this::mapRow);
    }

    @Override
    public List<EligibleResponder> findWithinRing(
            double latitude,
            double longitude,
            int radiusMeters,
            Collection<UUID> excludedUserIds) {
        if (maxRecipients <= 0 || radiusMeters <= 0) {
            return List.of();
        }
        MapSqlParameterSource parameters = commonParameters(latitude, longitude)
                .addValue("ringMeters", radiusMeters)
                .addValue("excluded", toArrayLiteral(excludedUserIds))
                .addValue("maxResults", maxRecipients);
        return jdbc.query(RING_SQL, parameters, this::mapRow);
    }

    /**
     * Literal de array do Postgres (`{a,b}` ou `{}`). Um `IN (:lista)` com a
     * lista vazia quebraria a consulta, e o array resolve o caso sem montar SQL
     * dinâmico — o plano continua o mesmo e o filtro por usuário é por chave.
     */
    private static String toArrayLiteral(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return "{}";
        }
        return userIds.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(",", "{", "}"));
    }

    private MapSqlParameterSource commonParameters(double latitude, double longitude) {
        return new MapSqlParameterSource()
                .addValue("latitude", latitude)
                .addValue("longitude", longitude)
                .addValue("defaultRadiusMeters", defaultRadiusMeters)
                .addValue("maxRadiusMeters", maxRadiusMeters)
                // O driver não infere o tipo de Instant; OffsetDateTime vira
                // TIMESTAMPTZ sem depender do timezone da sessão.
                .addValue("staleBefore", OffsetDateTime.now(ZoneOffset.UTC).minus(positionStaleAfter))
                .addValue("aliveSince", OffsetDateTime.now(ZoneOffset.UTC).minus(shiftIdleTimeout));
    }

    private EligibleResponder mapRow(java.sql.ResultSet rs, int rowNumber) throws java.sql.SQLException {
        return new EligibleResponder(
                rs.getObject("user_id", UUID.class),
                rs.getObject("shift_session_id", UUID.class),
                rs.getObject("post_id", UUID.class),
                rs.getObject("mobile_base_id", UUID.class),
                rs.getDouble("distance_m"),
                rs.getBoolean("position_stale"));
    }
}
