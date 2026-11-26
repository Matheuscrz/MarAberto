package com.maraberto.realtime.infra;

import com.maraberto.realtime.domain.PushChannel;
import com.maraberto.realtime.domain.PushSubscription;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persistência das inscrições de Web Push (VAPID) e tokens FCM por operador.
 */
@Repository
public class PushSubscriptionRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO push_subscriptions (user_id, channel, endpoint, p256dh, auth)
            VALUES (:userId, :channel, :endpoint, :p256dh, :auth)
            ON CONFLICT (endpoint) DO UPDATE SET
                user_id = EXCLUDED.user_id,
                channel = EXCLUDED.channel,
                p256dh = EXCLUDED.p256dh,
                auth = EXCLUDED.auth,
                updated_at = NOW()
            """;

    private static final String FIND_BY_USER_SQL = """
            SELECT id, user_id, channel, endpoint, p256dh, auth
            FROM push_subscriptions
            WHERE user_id = :userId
            ORDER BY updated_at DESC
            """;

    private static final String DELETE_BY_USER_AND_ENDPOINT_SQL = """
            DELETE FROM push_subscriptions
            WHERE user_id = :userId AND endpoint = :endpoint
            """;

    private static final String DELETE_BY_ENDPOINT_SQL = """
            DELETE FROM push_subscriptions
            WHERE endpoint = :endpoint
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PushSubscriptionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(UUID userId, PushChannel channel, String endpoint, String p256dh, String auth) {
        jdbc.update(UPSERT_SQL, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("channel", channel.name())
                .addValue("endpoint", endpoint)
                .addValue("p256dh", p256dh)
                .addValue("auth", auth));
    }

    public List<PushSubscription> findByUserId(UUID userId) {
        return jdbc.query(FIND_BY_USER_SQL, new MapSqlParameterSource("userId", userId),
                (rs, rowNum) -> new PushSubscription(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        PushChannel.valueOf(rs.getString("channel")),
                        rs.getString("endpoint"),
                        rs.getString("p256dh"),
                        rs.getString("auth")));
    }

    public void deleteByUserAndEndpoint(UUID userId, String endpoint) {
        jdbc.update(DELETE_BY_USER_AND_ENDPOINT_SQL, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("endpoint", endpoint));
    }

    public void deleteByEndpoint(String endpoint) {
        jdbc.update(DELETE_BY_ENDPOINT_SQL, new MapSqlParameterSource("endpoint", endpoint));
    }
}
