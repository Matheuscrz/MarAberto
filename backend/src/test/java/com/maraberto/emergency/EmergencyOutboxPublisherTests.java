package com.maraberto.emergency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.emergency.api.EmergencyCallDispatched;
import com.maraberto.emergency.api.EmergencyCallNotification;
import com.maraberto.emergency.application.EmergencyOutboxPublisher;
import com.maraberto.realtime.api.SosNotifierPort;
import com.maraberto.shared.security.crypto.SensitiveDataProtector;
import com.maraberto.support.EmergencyTestData;
import com.maraberto.support.PostgisIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "maraberto.emergency.outbox.sweep-interval=PT1H",
        "maraberto.emergency.outbox.sweep-initial-delay=PT1H",
        "maraberto.emergency.outbox.max-attempts=2"
})
@AutoConfigureMockMvc
@Import(EmergencyOutboxPublisherTests.NotifierTestConfiguration.class)
class EmergencyOutboxPublisherTests extends PostgisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SensitiveDataProtector sensitiveDataProtector;

    @Autowired
    private EmergencyOutboxPublisher publisher;

    @Autowired
    private RecordingSosNotifier notifier;

    @BeforeEach
    void reset() {
        EmergencyTestData.clearDispatchState(jdbcTemplate);
        notifier.reset();
    }

    @Test
    void publishesRightAfterCommitToTheAssignedOperator() throws Exception {
        double latitude = -15.7900;
        double longitude = -47.8800;
        UUID operatorId = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID postId = EmergencyTestData.createPost(jdbcTemplate, latitude, longitude, 500);
        EmergencyTestData.openPostShift(jdbcTemplate, operatorId, postId);

        UUID callId = createCall(latitude, longitude);

        assertThat(notifier.delivered())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.userId()).isEqualTo(operatorId);
                    EmergencyCallNotification notification = entry.notification();
                    assertThat(notification.callId()).isEqualTo(callId);
                    assertThat(notification.status()).isEqualTo("DISPATCHED");
                    assertThat(notification.dispatchId()).isNotNull();
                });

        String serialized = objectMapper.writeValueAsString(notifier.delivered().getFirst().notification());
        assertThat(serialized)
                .doesNotContain(String.valueOf(latitude))
                .doesNotContain(String.valueOf(longitude))
                .doesNotContain("clientRequestId");

        assertThat(outboxStatus(callId)).isEqualTo("PUBLISHED");
        assertThat(outboxAttempts(callId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM emergency_dispatches WHERE emergency_call_id = ?",
                String.class, callId))
                .isEqualTo("NOTIFIED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT notified_at FROM emergency_dispatches WHERE emergency_call_id = ?",
                Instant.class, callId))
                .isNotNull();
    }

    @Test
    void sweepRecoversPendingEventsLeftByACrash() throws Exception {
        UUID operatorId = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID callId = insertPendingDispatch(operatorId);

        int claimed = publisher.publishPending();

        assertThat(claimed).isGreaterThanOrEqualTo(1);
        assertThat(notifier.delivered())
                .anySatisfy(entry -> assertThat(entry.notification().callId()).isEqualTo(callId));
        assertThat(outboxStatus(callId)).isEqualTo("PUBLISHED");
    }

    @Test
    void retriesAndGivesUpAfterMaxAttempts() throws Exception {
        UUID operatorId = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID callId = insertPendingDispatch(operatorId);
        notifier.failWith(new IllegalStateException("transport indisponível"));

        publisher.publishPending();

        assertThat(outboxStatus(callId)).isEqualTo("PENDING");
        assertThat(outboxAttempts(callId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_error FROM emergency_outbox_events WHERE aggregate_id = ?",
                String.class, callId))
                .isEqualTo("IllegalStateException");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM emergency_dispatches WHERE emergency_call_id = ?",
                String.class, callId))
                .isEqualTo("PENDING");

        publisher.publishPending();

        assertThat(outboxStatus(callId)).isEqualTo("FAILED");
        assertThat(outboxAttempts(callId)).isEqualTo(2);

        assertThat(publisher.publishPending()).isZero();
    }

    private UUID createCall(double latitude, double longitude) throws Exception {
        String response = mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientRequestId": "%s",
                                  "latitude": %s,
                                  "longitude": %s,
                                  "accuracyMeters": 12.5
                                }
                                """.formatted(UUID.randomUUID(), latitude, longitude)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("callId").asText());
    }

    private UUID insertPendingDispatch(UUID operatorId) throws Exception {
        UUID callId = UUID.randomUUID();
        UUID dispatchId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO emergency_calls
                    (id, client_request_id, latitude, longitude, status)
                VALUES (?, ?, -15.79, -47.88, 'DISPATCHED')
                """, callId, UUID.randomUUID().toString());
        jdbcTemplate.update("""
                INSERT INTO emergency_dispatches
                    (id, emergency_call_id, round, recipient_user_id, status)
                VALUES (?, ?, 1, ?, 'PENDING')
                """, dispatchId, callId, operatorId);

        EmergencyCallDispatched dispatched = new EmergencyCallDispatched(
                callId, dispatchId, operatorId, null, null, 1, Instant.now());
        String payload = sensitiveDataProtector.encrypt(
                objectMapper.writeValueAsString(dispatched),
                SensitiveDataProtector.AAD_EMERGENCY_OUTBOX);
        jdbcTemplate.update("""
                INSERT INTO emergency_outbox_events (aggregate_id, event_type, payload, status)
                VALUES (?, ?, ?, 'PENDING')
                """, callId, EmergencyCallDispatched.class.getName(), payload);
        return callId;
    }

    private String outboxStatus(UUID callId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM emergency_outbox_events WHERE aggregate_id = ?",
                String.class, callId);
    }

    private Integer outboxAttempts(UUID callId) {
        return jdbcTemplate.queryForObject(
                "SELECT attempts FROM emergency_outbox_events WHERE aggregate_id = ?",
                Integer.class, callId);
    }

    record Delivery(UUID userId, EmergencyCallNotification notification) {
    }

    static class RecordingSosNotifier implements SosNotifierPort {

        private final List<Delivery> entries = new CopyOnWriteArrayList<>();
        private final AtomicBoolean failing = new AtomicBoolean(false);
        private volatile RuntimeException failure;

        @Override
        public void send(UUID userId, Object payload) {
            RuntimeException currentFailure = failure;
            if (failing.get() && currentFailure != null) {
                throw currentFailure;
            }
            entries.add(new Delivery(userId, (EmergencyCallNotification) payload));
        }

        void failWith(RuntimeException exception) {
            this.failure = exception;
            this.failing.set(true);
        }

        void reset() {
            entries.clear();
            failing.set(false);
            failure = null;
        }

        List<Delivery> delivered() {
            return List.copyOf(entries);
        }
    }

    @TestConfiguration
    static class NotifierTestConfiguration {

        @Bean
        @Primary
        RecordingSosNotifier recordingSosNotifier() {
            return new RecordingSosNotifier();
        }
    }
}
