package com.maraberto.occurrences.application;

import com.maraberto.emergency.api.SosCallCloser;
import com.maraberto.occurrences.api.CloseOccurrenceRequest;
import com.maraberto.occurrences.api.OccurrenceDetailView;
import com.maraberto.occurrences.api.RevealVictimRequest;
import com.maraberto.occurrences.api.UpdateOccurrenceRequest;
import com.maraberto.occurrences.api.VictimView;
import com.maraberto.occurrences.domain.CiospStatus;
import com.maraberto.occurrences.domain.OccurrenceAlreadyClosedException;
import com.maraberto.occurrences.domain.OccurrenceNotFoundException;
import com.maraberto.occurrences.domain.OccurrenceStatus;
import com.maraberto.occurrences.domain.OccurrenceTransitionException;
import com.maraberto.occurrences.domain.OccurrenceVictimNotFoundException;
import com.maraberto.occurrences.infra.OccurrenceRepository;
import com.maraberto.shared.security.crypto.SensitiveDataProtector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
public class OccurrenceCommandService {

    private final OccurrenceRepository occurrenceRepository;
    private final SensitiveDataProtector protector;
    private final SosCallCloser sosCallCloser;

    public OccurrenceCommandService(
            OccurrenceRepository occurrenceRepository,
            SensitiveDataProtector protector,
            SosCallCloser sosCallCloser) {
        this.occurrenceRepository = occurrenceRepository;
        this.protector = protector;
        this.sosCallCloser = sosCallCloser;
    }

    @Transactional
    public OccurrenceDetailView close(
            UUID occurrenceId,
            UUID userId,
            boolean allScopes,
            CloseOccurrenceRequest request) {
        OccurrenceRepository.OccurrenceDetail current = lock(occurrenceId, userId, allScopes);
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

        return detail(occurrenceId, userId, allScopes);
    }

    @Transactional
    public OccurrenceDetailView update(
            UUID occurrenceId,
            UUID userId,
            boolean allScopes,
            UpdateOccurrenceRequest request) {
        OccurrenceRepository.OccurrenceDetail current = lock(occurrenceId, userId, allScopes);

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

        return detail(occurrenceId, userId, allScopes);
    }

    @Transactional
    public VictimView reveal(
            UUID occurrenceId,
            UUID userId,
            boolean allScopes,
            RevealVictimRequest request) {
        OccurrenceRepository.OccurrenceDetail current = lock(occurrenceId, userId, allScopes);
        OccurrenceRepository.VictimData victim = current.victim();
        if (!victim.recorded()) {
            throw new OccurrenceVictimNotFoundException(occurrenceId);
        }

        occurrenceRepository.appendEvent(
                occurrenceId, "PERSON_REVEALED", userId,
                Map.of("reason", request.reason()));

        return VictimView.revealed(
                decrypt(victim.name()),
                decrypt(victim.age()),
                decrypt(victim.contact()),
                decrypt(victim.condition()));
    }

    private OccurrenceRepository.OccurrenceDetail lock(
            UUID occurrenceId, UUID userId, boolean allScopes) {
        return occurrenceRepository.findDetail(occurrenceId, userId, allScopes, true)
                .orElseThrow(() -> new OccurrenceNotFoundException(occurrenceId));
    }

    private OccurrenceDetailView detail(
            UUID occurrenceId, UUID userId, boolean allScopes) {
        return OccurrenceViews.detail(occurrenceRepository
                .findDetail(occurrenceId, userId, allScopes, false)
                .orElseThrow(() -> new OccurrenceNotFoundException(occurrenceId)));
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
