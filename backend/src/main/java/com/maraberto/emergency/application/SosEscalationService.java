package com.maraberto.emergency.application;

import com.maraberto.emergency.infra.EmergencyDispatchRepository;
import com.maraberto.identity.api.OperatorDirectory;
import com.maraberto.operations.api.EligibleResponder;
import com.maraberto.operations.api.EligibleRespondersPort;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escalonamento do SOS: abre o próximo anel de distância de um chamado que
 * ninguém assumiu (ADR-018).
 *
 * <p>A escada é durável e vive no banco. Cada chamado guarda quantos anéis já
 * foram consumidos ({@code escalation_step}) e quando abrir o próximo
 * ({@code next_escalation_at}); o job só reivindica as linhas vencidas com
 * {@code FOR UPDATE SKIP LOCKED}, então duas instâncias nunca abrem o mesmo anel
 * duas vezes.
 *
 * <p><strong>Quem já foi avisado não é avisado de novo.</strong> O conjunto de
 * exclusão são os operadores com despacho neste chamado (qualquer rodada), e a
 * consulta do anel mede a distância pela posição do plantão — a mesma regra do
 * despacho inicial, para não cair no cadastro da estrutura.
 *
 * <p><strong>Anéis vazios não gastam tempo.</strong> Se ninguém estiver dentro
 * de um anel, o seguinte é aberto na mesma passada: esperar 60 s para descobrir
 * que não há ninguém a 7 km quando já se sabe que não há ninguém a 5 km só
 * atrasaria o chamado. Quando a escada esgota, a coordenação é acionada (RN03) e
 * o escalonamento do chamado termina.
 */
@Service
public class SosEscalationService {

    private static final Logger log = LoggerFactory.getLogger(SosEscalationService.class);

    private static final String DUE_CALLS_SQL = """
            SELECT id, latitude, longitude, escalation_step
            FROM emergency_calls
            WHERE status = 'DISPATCHED'
              AND next_escalation_at IS NOT NULL
              AND next_escalation_at <= NOW()
            ORDER BY next_escalation_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """;

    private static final String SCHEDULE_NEXT_RING_SQL = """
            UPDATE emergency_calls
            SET escalation_step = :step, next_escalation_at = :nextAt, updated_at = NOW()
            WHERE id = :callId AND status = 'DISPATCHED'
            """;

    private static final String FINISH_ESCALATION_SQL = """
            UPDATE emergency_calls
            SET escalation_step = :step, next_escalation_at = NULL, updated_at = NOW()
            WHERE id = :callId AND status = 'DISPATCHED'
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final EligibleRespondersPort eligibleResponders;
    private final EmergencyDispatchRepository dispatchRepository;
    private final OperatorDirectory operatorDirectory;
    private final SosDispatchNotifier notifier;
    private final SosEscalationPolicy policy;
    private final int batchSize;

    public SosEscalationService(
            NamedParameterJdbcTemplate jdbc,
            EligibleRespondersPort eligibleResponders,
            EmergencyDispatchRepository dispatchRepository,
            OperatorDirectory operatorDirectory,
            SosDispatchNotifier notifier,
            SosEscalationPolicy policy,
            @Value("${maraberto.dispatch.escalation.batch-size:20}") int batchSize) {
        this.jdbc = jdbc;
        this.eligibleResponders = eligibleResponders;
        this.dispatchRepository = dispatchRepository;
        this.operatorDirectory = operatorDirectory;
        this.notifier = notifier;
        this.policy = policy;
        this.batchSize = batchSize;
    }

    /**
     * Abre, para cada chamado vencido, o próximo anel que encontrar alguém; se
     * nenhum anel restante encontrar ninguém, aciona a coordenação.
     */
    @Transactional
    public void escalateDueCalls() {
        if (!policy.enabled() || batchSize <= 0) {
            return;
        }
        List<DueCall> dueCalls = jdbc.query(
                DUE_CALLS_SQL,
                new MapSqlParameterSource("batchSize", batchSize),
                (resultSet, rowNumber) -> new DueCall(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getDouble("latitude"),
                        resultSet.getDouble("longitude"),
                        resultSet.getInt("escalation_step")));

        dueCalls.forEach(this::openNextRing);
    }

    private void openNextRing(DueCall call) {
        List<Integer> rings = policy.ringsMeters();
        Set<UUID> alreadyNotified =
                new HashSet<>(dispatchRepository.findNotifiedUserIds(call.callId()));
        Instant now = Instant.now();

        for (int index = call.step(); index < rings.size(); index++) {
            int ringMeters = rings.get(index);
            List<EligibleResponder> recipients = eligibleResponders.findWithinRing(
                    call.latitude(), call.longitude(), ringMeters, alreadyNotified);
            if (recipients.isEmpty()) {
                // Ninguém novo neste anel: abre o seguinte na mesma passada.
                continue;
            }
            int round = index + 2;
            notifier.notify(call.callId(), recipients, round, now);
            jdbc.update(SCHEDULE_NEXT_RING_SQL, new MapSqlParameterSource()
                    .addValue("callId", call.callId())
                    .addValue("step", index + 1)
                    // O driver não infere Instant; OffsetDateTime vira TIMESTAMPTZ.
                    .addValue("nextAt", OffsetDateTime.ofInstant(
                            policy.deadlineFrom(now), ZoneOffset.UTC)));
            log.info("SOS {} ampliado para o anel de {} m (rodada {}), {} destinatário(s)",
                    call.callId(), ringMeters, round, recipients.size());
            return;
        }

        notifyCoordination(call.callId(), alreadyNotified, rings.size() + 2, now);
        jdbc.update(FINISH_ESCALATION_SQL, new MapSqlParameterSource()
                .addValue("callId", call.callId())
                .addValue("step", rings.size()));
        log.warn("SOS {} esgotou a escada de escalonamento sem ack; coordenação acionada",
                call.callId());
    }

    private void notifyCoordination(UUID callId, Set<UUID> alreadyNotified, int round, Instant now) {
        List<EligibleResponder> coordination = operatorDirectory.activeCoordinationUserIds()
                .stream()
                .filter(userId -> !alreadyNotified.contains(userId))
                .map(EligibleResponder::coordination)
                .toList();
        if (coordination.isEmpty()) {
            return;
        }
        notifier.notify(callId, coordination, round, now);
    }

    private record DueCall(UUID callId, double latitude, double longitude, int step) {
    }
}
