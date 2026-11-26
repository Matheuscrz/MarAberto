package com.maraberto.operations.application;

import com.maraberto.audit.api.AuditActions;
import com.maraberto.audit.api.AuditEntityTypes;
import com.maraberto.audit.api.AuditEvent;
import com.maraberto.audit.api.AuditTrail;
import com.maraberto.operations.api.LaunchFlagRequest;
import com.maraberto.operations.api.PublicUnmappedSignal;
import com.maraberto.operations.api.UnmappedSignalDecisionRequest;
import com.maraberto.operations.api.UnmappedSignalView;
import com.maraberto.operations.domain.UnmappedSignalNotFoundException;
import com.maraberto.operations.domain.UnmappedSignalStatus;
import com.maraberto.operations.infra.JdbcUnmappedSignalRepository;
import com.maraberto.operations.infra.PublicOperationsRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fila de triagem da sinalização feita fora do mapeamento (RF12/RN17).
 *
 * <p>O registro nasce no lançamento por localização quando nenhuma área ativa
 * contém o ponto. Ele **não** é uma bandeira de praia — não há área nem setor
 * para herdar —, mas aparece no mapa público e no mapa de situação como alerta
 * de área não mapeada enquanto estiver pendente e dentro da validade. A
 * coordenação decide o destino na triagem — identificar numa área existente,
 * pedir um novo mapeamento ou descartar.
 *
 * <p>A área mais próxima é devolvida como sugestão; o vínculo só é gravado com
 * a decisão humana. Uma decisão pode ser revista (inclusive reaberta com
 * {@code PENDING}), porque errar a triagem não pode virar registro imutável.
 */
@Service
public class UnmappedSignalService {

    private static final int MAX_LIST_SIZE = 200;

    private final JdbcUnmappedSignalRepository repository;
    private final PublicOperationsRepository publicRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditTrail auditTrail;

    public UnmappedSignalService(
            JdbcUnmappedSignalRepository repository,
            PublicOperationsRepository publicRepository,
            NamedParameterJdbcTemplate jdbc,
            AuditTrail auditTrail) {
        this.repository = repository;
        this.publicRepository = publicRepository;
        this.jdbc = jdbc;
        this.auditTrail = auditTrail;
    }

    /**
     * Guarda o lançamento que caiu fora do mapeamento.
     *
     * <p>As fotos já foram validadas pelo chamador (existem e são de quem está
     * lançando); aqui só entram na tabela de ligação do registro. A validade é
     * a mesma da bandeira e começa a contar no lançamento: o alerta não espera
     * a triagem para aparecer, nem fica no mapa depois de vencer.
     */
    @Transactional
    public UnmappedSignalView record(
            UUID reportedBy,
            UUID reportedPostId,
            LaunchFlagRequest request,
            List<UUID> mediaIds,
            Instant validUntil) {
        UUID id = repository.insert(
                reportedBy,
                reportedPostId,
                request.flag(),
                blankToNull(request.notes()),
                request.latitude(),
                request.longitude(),
                request.accuracyMeters(),
                validUntil);
        repository.attachMedia(id, mediaIds);
        return repository.findById(id)
                .orElseThrow(UnmappedSignalNotFoundException::new);
    }

    /**
     * Alertas vigentes para o mapa público.
     *
     * <p>Devolve apenas registros {@code PENDING} dentro da validade. Decidir
     * na triagem ou deixar o alerta vencer tira o pin do mapa; a linha continua
     * na fila da coordenação.
     */
    @Transactional(readOnly = true)
    public List<PublicUnmappedSignal> publicAlerts() {
        return publicRepository.findActiveUnmappedAlerts();
    }

    /**
     * Fila de triagem.
     *
     * @param status estado filtrado; {@code null} devolve todos
     * @param limit  máximo de registros (1 a 200)
     */
    @Transactional(readOnly = true)
    public List<UnmappedSignalView> list(UnmappedSignalStatus status, int limit) {
        return list(status, limit, null, true);
    }

    @Transactional(readOnly = true)
    public List<UnmappedSignalView> list(
            UnmappedSignalStatus status, int limit, UUID requesterId, boolean unrestricted) {
        int size = Math.max(1, Math.min(limit, MAX_LIST_SIZE));
        return repository.findAll(status, size, requesterId, unrestricted);
    }

    /**
     * Decide o destino do registro.
     *
     * <p>{@code IDENTIFIED} exige uma área ativa e, quando há setor, que ele
     * pertença à área. As demais decisões não associam área — quem quer apenas
     * pedir o mapeamento não deve deixar um vínculo meio feito.
     */
    @Transactional
    public UnmappedSignalView decide(
            UUID coordinatorId,
            UUID signalId,
            UnmappedSignalDecisionRequest request) {
        return decide(coordinatorId, false, signalId, request);
    }

    @Transactional
    public UnmappedSignalView decide(
            UUID coordinatorId,
            boolean unrestricted,
            UUID signalId,
            UnmappedSignalDecisionRequest request) {
        repository.findById(signalId, coordinatorId, unrestricted)
                .orElseThrow(UnmappedSignalNotFoundException::new);
        validate(request, coordinatorId, unrestricted);
        repository.decide(
                signalId,
                request.decision(),
                request.beachId(),
                request.sectorId(),
                blankToNull(request.notes()),
                coordinatorId,
                Instant.now());
        auditTrail.record(AuditEvent.of(
                coordinatorId,
                AuditActions.UNMAPPED_SIGNAL_DECIDED,
                AuditEntityTypes.UNMAPPED_SIGNAL,
                signalId,
                decisionDetail(request)));
        return repository.findById(signalId)
                .orElseThrow(UnmappedSignalNotFoundException::new);
    }

    private static Map<String, Object> decisionDetail(UnmappedSignalDecisionRequest request) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("decision", request.decision().name());
        if (request.beachId() != null) {
            detail.put("beachId", request.beachId().toString());
        }
        if (request.sectorId() != null) {
            detail.put("sectorId", request.sectorId().toString());
        }
        return detail;
    }

    private void validate(
            UnmappedSignalDecisionRequest request, UUID coordinatorId, boolean unrestricted) {
        if (request.decision() != UnmappedSignalStatus.IDENTIFIED) {
            if (request.beachId() != null || request.sectorId() != null) {
                throw new IllegalArgumentException(
                        "A decisão " + request.decision() + " não associa área de banho");
            }
            return;
        }
        if (request.beachId() == null) {
            throw new IllegalArgumentException(
                    "Identificar o local exige a área de banho (beachId)");
        }
        requireActiveBeach(request.beachId());
        if (!unrestricted) {
            requireBeachInScope(coordinatorId, request.beachId());
        }
        if (request.sectorId() != null) {
            requireSectorOfBeach(request.beachId(), request.sectorId());
        }
    }

    private void requireBeachInScope(UUID userId, UUID beachId) {
        Boolean allowed = jdbc.query("""
                        SELECT NOT EXISTS (
                                   SELECT 1 FROM user_beach_scopes WHERE user_id = :userId
                               )
                            OR EXISTS (
                                   SELECT 1
                                     FROM user_beach_scopes
                                    WHERE user_id = :userId
                                      AND beach_id = :beachId
                               )
                        """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("beachId", beachId),
                rs -> rs.next() ? rs.getBoolean(1) : false);
        if (!Boolean.TRUE.equals(allowed)) {
            throw new IllegalArgumentException(
                    "Área de banho fora do seu escopo territorial");
        }
    }

    private void requireActiveBeach(UUID beachId) {
        Boolean active = jdbc.query(
                "SELECT active FROM beaches WHERE id = :id",
                new MapSqlParameterSource("id", beachId),
                rs -> rs.next() ? rs.getBoolean("active") : null);
        if (!Boolean.TRUE.equals(active)) {
            throw new IllegalArgumentException("Área de banho inexistente ou inativa");
        }
    }

    private void requireSectorOfBeach(UUID beachId, UUID sectorId) {
        Optional<UUID> owner = jdbc.query("""
                        SELECT beach_id
                          FROM beach_sectors
                         WHERE id = :id
                           AND active
                        """,
                new MapSqlParameterSource("id", sectorId),
                (rs, rowNum) -> rs.getObject("beach_id", UUID.class)).stream().findFirst();
        if (owner.isEmpty() || !owner.get().equals(beachId)) {
            throw new IllegalArgumentException("Setor inexistente ou não pertence à área informada");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
