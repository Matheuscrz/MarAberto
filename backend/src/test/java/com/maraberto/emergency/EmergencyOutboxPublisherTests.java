package com.maraberto.emergency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.emergency.api.EmergencyCallCreated;
import com.maraberto.emergency.api.EmergencyCallNotification;
import com.maraberto.emergency.application.EmergencyOutboxPublisher;
import com.maraberto.realtime.api.RealtimePublisherPort;
import com.maraberto.shared.security.crypto.SensitiveDataProtector;
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
@Import(EmergencyOutboxPublisherTests.RealtimeTestConfiguration.class)
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
    private RecordingRealtimePublisher realtimePublisher;

    @BeforeEach
    void reset() {
        realtimePublisher.reset();
    }

    @Test
    void publishesRightAfterCommitWithRedactedPayload() throws Exception {
        String response = mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientRequestId": "%s",
                                  "latitude": -10.9472,
                                  "longitude": -37.0731,
                                  "accuracyMeters": 12.5
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID callId = UUID.fromString(objectMapper.readTree(response).get("callId").asText());

        assertThat(realtimePublisher.published())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.destination())
                            .isEqualTo("/topic/operational.emergency.calls");
                    EmergencyCallNotification notification = (EmergencyCallNotification) entry.payload();
                    assertThat(notification.callId()).isEqualTo(callId);
                    assertThat(notification.status()).isEqualTo("DISPATCHED");
                });

        String serialized = objectMapper.writeValueAsString(realtimePublisher.published().getFirst().payload());
        assertThat(serialized)
                .doesNotContain("-10.9472")
                .doesNotContain("-37.0731")
                .doesNotContain("clientRequestId");
        assertThat(outboxStatus(callId)).isEqualTo("PUBLISHED");
        assertThat(outboxAttempts(callId)).isZero();
    }

    @Test
    void sweepRecoversPendingEventsLeftByACrash() throws Exception {
        UUID callId = UUID.randomUUID();
        insertPendingEvent(callId);

        int claimed = publisher.publishPending();

        assertThat(claimed).isGreaterThanOrEqualTo(1);
        assertThat(realtimePublisher.published())
                .anySatisfy(entry -> assertThat(((EmergencyCallNotification) entry.payload()).callId())
                        .isEqualTo(callId));
        assertThat(outboxStatus(callId)).isEqualTo("PUBLISHED");
    }

    @Test
    void retriesAndGivesUpAfterMaxAttempts() throws Exception {
        UUID callId = UUID.randomUUID();
        insertPendingEvent(callId);
        realtimePublisher.failWith(new IllegalStateException("broker indisponível"));

        publisher.publishPending();

        assertThat(outboxStatus(callId)).isEqualTo("PENDING");
        assertThat(outboxAttempts(callId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_error FROM emergency_outbox_events WHERE aggregate_id = ?",
                String.class,
                callId))
                .isEqualTo("IllegalStateException");

        publisher.publishPending();

        assertThat(outboxStatus(callId)).isEqualTo("FAILED");
        assertThat(outboxAttempts(callId)).isEqualTo(2);

        assertThat(publisher.publishPending()).isZero();
    }

    private void insertPendingEvent(UUID callId) throws Exception {
        EmergencyCallCreated created = new EmergencyCallCreated(
                callId,
                UUID.randomUUID().toString(),
                -10.9472,
                -37.0731,
                12.5,
                Instant.now());
        String payload = sensitiveDataProtector.encrypt(
                objectMapper.writeValueAsString(created),
                SensitiveDataProtector.AAD_EMERGENCY_OUTBOX);

        jdbcTemplate.update("""
                INSERT INTO emergency_outbox_events (aggregate_id, event_type, payload, status)
                VALUES (?, ?, ?, 'PENDING')
                """, callId, EmergencyCallCreated.class.getName(), payload);
    }

    private String outboxStatus(UUID callId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM emergency_outbox_events WHERE aggregate_id = ?",
                String.class,
                callId);
    }

    private Integer outboxAttempts(UUID callId) {
        return jdbcTemplate.queryForObject(
                "SELECT attempts FROM emergency_outbox_events WHERE aggregate_id = ?",
                Integer.class,
                callId);
    }

    record PublishedEntry(String destination, Object payload) {
    }

    static class RecordingRealtimePublisher implements RealtimePublisherPort {

        private final List<PublishedEntry> entries = new CopyOnWriteArrayList<>();
        private final AtomicBoolean failing = new AtomicBoolean(false);
        private volatile RuntimeException failure;

        @Override
        public void publish(String destination, Object payload) {
            RuntimeException currentFailure = failure;
            if (failing.get() && currentFailure != null) {
                throw currentFailure;
            }
            entries.add(new PublishedEntry(destination, payload));
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

        List<PublishedEntry> published() {
            return List.copyOf(entries);
        }
    }

    @TestConfiguration
    static class RealtimeTestConfiguration {

        @Bean
        @Primary
        RecordingRealtimePublisher recordingRealtimePublisher() {
            return new RecordingRealtimePublisher();
        }
    }
}
