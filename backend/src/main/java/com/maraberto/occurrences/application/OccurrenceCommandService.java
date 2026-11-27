package com.maraberto.occurrences.application;

import com.maraberto.audit.api.AuditActions;
import com.maraberto.audit.api.AuditEntityTypes;
import com.maraberto.audit.api.AuditEvent;
import com.maraberto.audit.api.AuditTrail;
import com.maraberto.emergency.api.SosCallCloser;
import com.maraberto.occurrences.api.CloseOccurrenceRequest;
import com.maraberto.occurrences.api.CreateOccurrenceRequest;
import com.maraberto.occurrences.api.CreateOccurrenceResponse;
import com.maraberto.occurrences.api.DuplicateCandidateView;
import com.maraberto.occurrences.api.OccurrenceDetailView;
import com.maraberto.occurrences.api.RevealVictimRequest;
import com.maraberto.occurrences.api.UpdateOccurrenceRequest;
import com.maraberto.occurrences.api.VictimView;
import com.maraberto.occurrences.domain.CiospStatus;
import com.maraberto.occurrences.domain.OccurrenceAlreadyClosedException;
import com.maraberto.occurrences.domain.OccurrenceNotFoundException;
import com.maraberto.occurrences.domain.OccurrenceOrigin;
import com.maraberto.occurrences.domain.OccurrenceOutsideScopeException;
import com.maraberto.occurrences.domain.OccurrenceStatus;
import com.maraberto.occurrences.domain.OccurrenceTransitionException;
import com.maraberto.occurrences.domain.OccurrenceVictimNotFoundException;
import com.maraberto.occurrences.infra.OccurrenceRepository;
import com.maraberto.shared.security.crypto.SensitiveDataProtector;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encerramento e edição da ocorrência (ADR-013 emendada).
 *
 * <p>Encerrar tira a ficha da lista "em atendimento" e encerra o chamado do SOS
 * na mesma transação, pelo portão público de {@code emergency}. Editar não
 * mexe em estado: serve para a coordenação completar o que só apareceu depois
 * (vítima identificada, protocolo do CIOSP, correção do relato).
 */
@Service
@EnableConfigurationProperties(OccurrenceProperties.class)
public class OccurrenceCommandService {

    private final OccurrenceRepository occurrenceRepository;
    private final SensitiveDataProtector protector;
    private final SosCallCloser sosCallCloser;
    private final AuditTrail auditTrail;
    private final OccurrenceProperties properties;

    public OccurrenceCommandService(
            OccurrenceRepository occurrenceRepository,
            SensitiveDataProtector protector,
            SosCallCloser sosCallCloser,
            AuditTrail auditTrail,
            OccurrenceProperties properties) {
        this.occurrenceRepository = occurrenceRepository;
        this.protector = protector;
        this.sosCallCloser = sosCallCloser;
        this.auditTrail = auditTrail;
        this.properties = properties;
    }

    @Transactional
    public CreateOccurrenceResponse create(
            UUID userId,
            boolean allScopes,
            CreateOccurrenceRequest request) {
        return create(userId, allScopes, false, request);
    }

    @Transactional
    public CreateOccurrenceResponse create(
            UUID userId,
            boolean allScopes,
            boolean bypassTerritorialScope,
            CreateOccurrenceRequest request) {
        validateManualCreate(request);
        if (!bypassTerritorialScope) {
            UUID targetBeachId = request.beachId() != null
                    ? request.beachId()
                    : (request.latitude() != null && request.longitude() != null
                            ? occurrenceRepository.resolveBeachIdForPoint(
                                    request.latitude(), request.longitude()).orElse(null)
                            : null);
            if (targetBeachId != null && !occurrenceRepository.isBeachInScope(userId, targetBeachId)) {
                throw new OccurrenceOutsideScopeException();
            }
        }

        Optional<UUID> existingId = occurrenceRepository.findIdByClientRequestId(
                request.clientRequestId());
        if (existingId.isPresent()) {
            OccurrenceDetailView existing = detail(
                    existingId.get(), userId, allScopes, bypassTerritorialScope);
            return new CreateOccurrenceResponse(
                    existing,
                    findDuplicates(existing.occurrence(), userId, allScopes, bypassTerritorialScope));
        }

        String referenceText = clean(request.referenceText());
        String summary = clean(request.summary());
        String outcomeNotes = clean(request.outcomeNotes());
        String externalProtocol = clean(request.externalProtocol());
        String ciospProtocol = clean(request.ciospProtocol());

        OccurrenceStatus status = request.outcome() != null
                ? request.outcome().status()
                : OccurrenceStatus.OPEN;
        CiospStatus ciospStatus = resolveCiospOnCreate(request, ciospProtocol);

        OccurrenceRepository.ManualInsert insert = new OccurrenceRepository.ManualInsert(
                request.clientRequestId(),
                request.origin(),
                request.nature(),
                status,
                request.outcome(),
                request.occurredAt(),
                request.latitude(),
                request.longitude(),
                request.accuracyMeters(),
                request.beachId(),
                request.sectorId(),
                request.postId(),
                referenceText,
                encrypt(summary, SensitiveDataProtector.AAD_OCCURRENCE_SUMMARY),
                encrypt(outcomeNotes, SensitiveDataProtector.AAD_OCCURRENCE_OUTCOME),
                externalProtocol,
                ciospStatus,
                ciospProtocol,
                encryptOptional(request.victimName()),
                encryptOptional(request.victimAge()),
                encryptOptional(request.victimContact()),
                encryptOptional(request.victimCondition()),
                userId);

        Optional<UUID> insertedId = occurrenceRepository.insertManual(insert);
        if (insertedId.isEmpty()) {
            UUID id = occurrenceRepository.findIdByClientRequestId(request.clientRequestId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Conflito de clientRequestId sem registro correspondente."));
            OccurrenceDetailView existing = detail(id, userId, allScopes, bypassTerritorialScope);
            return new CreateOccurrenceResponse(
                    existing,
                    findDuplicates(existing.occurrence(), userId, allScopes, bypassTerritorialScope));
        }

        UUID occurrenceId = insertedId.get();
        occurrenceRepository.appendEvent(
                occurrenceId,
                "OPENED",
                userId,
                Map.of("origin", request.origin().name()));

        if (request.outcome() != null) {
            occurrenceRepository.appendEvent(
                    occurrenceId,
                    "STATUS_CHANGED",
                    userId,
                    Map.of(
                            "from", OccurrenceStatus.OPEN.name(),
                            "to", status.name(),
                            "outcome", request.outcome().name()));
        }
        if (ciospStatus == CiospStatus.REGISTERED) {
            occurrenceRepository.appendEvent(
                    occurrenceId,
                    "CIOSP_REGISTERED",
                    userId,
                    Map.of("status", CiospStatus.REGISTERED.name()));
        }

        auditTrail.record(AuditEvent.of(
                userId,
                AuditActions.OCCURRENCE_CREATED,
                AuditEntityTypes.OCCURRENCE,
                occurrenceId,
                Map.of(
                        "origin", request.origin().name(),
                        "nature", request.nature().name(),
                        "status", status.name())));

        OccurrenceDetailView created = detail(
                occurrenceId, userId, allScopes, bypassTerritorialScope);
        return new CreateOccurrenceResponse(
                created,
                findDuplicates(created.occurrence(), userId, allScopes, bypassTerritorialScope));
    }

    @Transactional
    public OccurrenceDetailView close(
            UUID occurrenceId,
            UUID userId,
            boolean allScopes,
            CloseOccurrenceRequest request) {
        return close(occurrenceId, userId, allScopes, false, request);
    }

    @Transactional
    public OccurrenceDetailView close(
            UUID occurrenceId,
            UUID userId,
            boolean allScopes,
            boolean bypassTerritorialScope,
            CloseOccurrenceRequest request) {
        OccurrenceRepository.OccurrenceDetail current = lock(
                occurrenceId, userId, allScopes, bypassTerritorialScope);
        if (isClosed(current.view().status())) {
            throw new OccurrenceAlreadyClosedException(occurrenceId);
        }

        String protocol = clean(request.ciospProtocol());
        CiospStatus ciospStatus = resolveCiospOnClose(request, current, protocol);

        OccurrenceRepository.CloseWrite write = new OccurrenceRepository.CloseWrite(
                request.outcome().status(),
                request.nature() == null ? current.view().nature() : request.nature(),
                request.outcome(),
                ciospStatus,
                encrypt(clean(request.summary()), SensitiveDataProtector.AAD_OCCURRENCE_SUMMARY),
                encrypt(clean(request.outcomeNotes()),
                        SensitiveDataProtector.AAD_OCCURRENCE_OUTCOME),
                clean(request.referenceText()),
                clean(request.externalProtocol()),
                protocol,
                encryptOptional(request.victimName()),
                encryptOptional(request.victimAge()),
                encryptOptional(request.victimContact()),
                encryptOptional(request.victimCondition()),
                userId);

        occurrenceRepository.close(occurrenceId, write);

        Map<String, Object> transition = new LinkedHashMap<>();
        transition.put("from", current.view().status().name());
        transition.put("to", write.status().name());
        transition.put("outcome", request.outcome().name());
        occurrenceRepository.appendEvent(occurrenceId, "STATUS_CHANGED", userId, transition);
        if (ciospStatus == CiospStatus.REGISTERED
                && current.view().ciospStatus() != CiospStatus.REGISTERED) {
            occurrenceRepository.appendEvent(
                    occurrenceId, "CIOSP_REGISTERED", userId,
                    Map.of("status", CiospStatus.REGISTERED.name()));
        }

        if (current.view().emergencyCallId() != null) {
            sosCallCloser.closeFromOccurrence(
                    current.view().emergencyCallId(),
                    request.outcome().sosOutcome(),
                    userId);
        }

        auditTrail.record(AuditEvent.of(
                userId,
                AuditActions.OCCURRENCE_CLOSED,
                AuditEntityTypes.OCCURRENCE,
                occurrenceId,
                Map.of(
                        "from", current.view().status().name(),
                        "to", write.status().name(),
                        "outcome", request.outcome().name())));

        return detail(occurrenceId, userId, allScopes, bypassTerritorialScope);
    }

    @Transactional
    public OccurrenceDetailView update(
            UUID occurrenceId,
            UUID userId,
            boolean allScopes,
            UpdateOccurrenceRequest request) {
        return update(occurrenceId, userId, allScopes, false, request);
    }

    @Transactional
    public OccurrenceDetailView update(
            UUID occurrenceId,
            UUID userId,
            boolean allScopes,
            boolean bypassTerritorialScope,
            UpdateOccurrenceRequest request) {
        OccurrenceRepository.OccurrenceDetail current = lock(
                occurrenceId, userId, allScopes, bypassTerritorialScope);

        String protocol = clean(request.ciospProtocol());
        CiospStatus ciospStatus = resolveCiospOnUpdate(request, current, protocol);

        OccurrenceRepository.UpdateWrite write = new OccurrenceRepository.UpdateWrite(
                request.nature(),
                encryptNullable(request.summary(), SensitiveDataProtector.AAD_OCCURRENCE_SUMMARY),
                encryptNullable(request.outcomeNotes(),
                        SensitiveDataProtector.AAD_OCCURRENCE_OUTCOME),
                clean(request.referenceText()),
                clean(request.externalProtocol()),
                protocol,
                ciospStatus,
                encryptOptional(request.victimName()),
                encryptOptional(request.victimAge()),
                encryptOptional(request.victimContact()),
                encryptOptional(request.victimCondition()));

        occurrenceRepository.update(occurrenceId, write, userId);

        List<String> fields = changedFields(request, protocol, ciospStatus);
        if (!fields.isEmpty()) {
            occurrenceRepository.appendEvent(
                    occurrenceId, "EDITED", userId, Map.of("fields", fields));
        }
        if (ciospStatus == CiospStatus.REGISTERED
                && current.view().ciospStatus() != CiospStatus.REGISTERED) {
            occurrenceRepository.appendEvent(
                    occurrenceId, "CIOSP_REGISTERED", userId,
                    Map.of("status", CiospStatus.REGISTERED.name()));
        }

        if (!fields.isEmpty()) {
            auditTrail.record(AuditEvent.of(
                    userId,
                    AuditActions.OCCURRENCE_EDITED,
                    AuditEntityTypes.OCCURRENCE,
                    occurrenceId,
                    Map.of("fields", fields)));
        }

        return detail(occurrenceId, userId, allScopes, bypassTerritorialScope);
    }

    @Transactional
    public VictimView reveal(
            UUID occurrenceId,
            UUID userId,
            boolean allScopes,
            RevealVictimRequest request) {
        return reveal(occurrenceId, userId, allScopes, false, request);
    }

    @Transactional
    public VictimView reveal(
            UUID occurrenceId,
            UUID userId,
            boolean allScopes,
            boolean bypassTerritorialScope,
            RevealVictimRequest request) {
        OccurrenceRepository.OccurrenceDetail current = lock(
                occurrenceId, userId, allScopes, bypassTerritorialScope);
        OccurrenceRepository.VictimData victim = current.victim();
        if (!victim.recorded()) {
            throw new OccurrenceVictimNotFoundException(occurrenceId);
        }

        occurrenceRepository.appendEvent(
                occurrenceId, "PERSON_REVEALED", userId,
                Map.of("reason", request.reason()));

        auditTrail.record(AuditEvent.of(
                userId,
                AuditActions.PERSON_REVEALED,
                AuditEntityTypes.OCCURRENCE,
                occurrenceId,
                Map.of()));

        return VictimView.revealed(
                decrypt(victim.name()),
                decrypt(victim.age()),
                decrypt(victim.contact()),
                decrypt(victim.condition()));
    }

    private OccurrenceRepository.OccurrenceDetail lock(
            UUID occurrenceId, UUID userId, boolean allScopes, boolean bypassTerritorialScope) {
        return occurrenceRepository
                .findDetail(occurrenceId, userId, allScopes, bypassTerritorialScope, true)
                .orElseThrow(() -> new OccurrenceNotFoundException(occurrenceId));
    }

    private OccurrenceDetailView detail(
            UUID occurrenceId, UUID userId, boolean allScopes, boolean bypassTerritorialScope) {
        return OccurrenceViews.detail(occurrenceRepository
                .findDetail(occurrenceId, userId, allScopes, bypassTerritorialScope, false)
                .orElseThrow(() -> new OccurrenceNotFoundException(occurrenceId)));
    }

    private void validateManualCreate(CreateOccurrenceRequest request) {
        if (request.origin() == OccurrenceOrigin.SOS_CITIZEN) {
            throw new IllegalArgumentException(
                    "Registro manual aceita apenas CBM_193, EXTERNAL_OTHER ou FIELD_ATTENDED.");
        }
        boolean hasLat = request.latitude() != null;
        boolean hasLon = request.longitude() != null;
        if (hasLat != hasLon) {
            throw new IllegalArgumentException(
                    "Latitude e longitude devem ser informadas juntas.");
        }
        if (hasLat) {
            if (request.latitude() < -90.0 || request.latitude() > 90.0) {
                throw new IllegalArgumentException("Latitude deve estar entre -90 e 90.");
            }
            if (request.longitude() < -180.0 || request.longitude() > 180.0) {
                throw new IllegalArgumentException("Longitude deve estar entre -180 e 180.");
            }
            if (request.accuracyMeters() != null && request.accuracyMeters() < 0) {
                throw new IllegalArgumentException("Precisão da coordenada não pode ser negativa.");
            }
        } else if (request.beachId() == null || clean(request.referenceText()) == null) {
            throw new IllegalArgumentException(
                    "Informe as coordenadas ou a praia com ponto de referência.");
        }
        if (request.beachId() != null && !occurrenceRepository.existsBeach(request.beachId())) {
            throw new IllegalArgumentException("Área de banho informada não foi encontrada.");
        }
        if (request.sectorId() != null
                && !occurrenceRepository.existsSector(request.sectorId(), request.beachId())) {
            throw new IllegalArgumentException("Setor informado não pertence à área ou não existe.");
        }
    }

    private List<DuplicateCandidateView> findDuplicates(
            com.maraberto.occurrences.api.OccurrenceView occurrence,
            UUID userId,
            boolean allScopes,
            boolean bypassTerritorialScope) {
        if (occurrence.latitude() == null || occurrence.longitude() == null) {
            return List.of();
        }
        Instant from = occurrence.occurredAt().minus(properties.duplicateWindow());
        Instant to = occurrence.occurredAt().plus(properties.duplicateWindow());
        return occurrenceRepository.findNearbyDuplicates(
                userId,
                allScopes,
                bypassTerritorialScope,
                occurrence.id(),
                occurrence.latitude(),
                occurrence.longitude(),
                from,
                to,
                properties.duplicateRadiusMeters());
    }

    private static CiospStatus resolveCiospOnCreate(
            CreateOccurrenceRequest request, String protocol) {
        if (protocol != null) {
            return CiospStatus.REGISTERED;
        }
        if (request.outcome() != null && !request.outcome().requiresCiosp()) {
            return CiospStatus.NOT_REQUIRED;
        }
        return CiospStatus.PENDING;
    }

    private static boolean isClosed(OccurrenceStatus status) {
        return status == OccurrenceStatus.CLOSED
                || status == OccurrenceStatus.CANCELLED
                || status == OccurrenceStatus.FALSE_ALARM;
    }

    private static CiospStatus resolveCiospOnClose(
            CloseOccurrenceRequest request,
            OccurrenceRepository.OccurrenceDetail current,
            String protocol) {
        if (protocol != null) {
            return CiospStatus.REGISTERED;
        }
        if (!request.outcome().requiresCiosp()) {
            return CiospStatus.NOT_REQUIRED;
        }
        return current.view().ciospStatus() == CiospStatus.REGISTERED
                ? CiospStatus.REGISTERED
                : CiospStatus.PENDING;
    }

    private static CiospStatus resolveCiospOnUpdate(
            UpdateOccurrenceRequest request,
            OccurrenceRepository.OccurrenceDetail current,
            String protocol) {
        if (protocol != null) {
            if (request.ciospStatus() != null && request.ciospStatus() != CiospStatus.REGISTERED) {
                throw new OccurrenceTransitionException(
                        current.view().id(),
                        "https://maraberto.org/problems/ciosp-status-conflict",
                        "Informar o protocolo do CIOSP marca a ocorrência como registrada.");
            }
            return CiospStatus.REGISTERED;
        }
        if (request.ciospStatus() == CiospStatus.REGISTERED
                && current.ciospProtocol() == null) {
            throw new OccurrenceTransitionException(
                    current.view().id(),
                    "https://maraberto.org/problems/ciosp-protocol-required",
                    "Para marcar como registrada no CIOSP, informe o protocolo.");
        }
        return request.ciospStatus();
    }

    private static List<String> changedFields(
            UpdateOccurrenceRequest request, String protocol, CiospStatus ciospStatus) {
        List<String> fields = new ArrayList<>();
        if (request.nature() != null) fields.add("nature");
        if (clean(request.summary()) != null) fields.add("summary");
        if (request.outcomeNotes() != null) fields.add("outcomeNotes");
        if (request.referenceText() != null) fields.add("referenceText");
        if (request.externalProtocol() != null) fields.add("externalProtocol");
        if (protocol != null) fields.add("ciospProtocol");
        if (ciospStatus != null) fields.add("ciospStatus");
        if (clean(request.victimName()) != null) fields.add("victimName");
        if (clean(request.victimAge()) != null) fields.add("victimAge");
        if (clean(request.victimContact()) != null) fields.add("victimContact");
        if (clean(request.victimCondition()) != null) fields.add("victimCondition");
        return fields;
    }

    private String encrypt(String value, String aad) {
        return value == null ? null : protector.encrypt(value, aad);
    }

    private String encryptNullable(String value, String aad) {
        return encrypt(clean(value), aad);
    }

    private String encryptOptional(String value) {
        String cleaned = clean(value);
        return cleaned == null
                ? null
                : protector.encrypt(cleaned, SensitiveDataProtector.AAD_OCCURRENCE_VICTIM);
    }

    private String decrypt(String value) {
        return value == null
                ? null
                : protector.decryptOrPlaintext(
                        value, SensitiveDataProtector.AAD_OCCURRENCE_VICTIM);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
