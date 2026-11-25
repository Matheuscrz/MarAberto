package com.maraberto.content.application;

import com.maraberto.audit.api.AuditActions;
import com.maraberto.audit.api.AuditEntityTypes;
import com.maraberto.audit.api.AuditEvent;
import com.maraberto.audit.api.AuditTrail;
import com.maraberto.content.api.ContentChanged;
import com.maraberto.content.api.ContentKind;
import com.maraberto.content.api.ContentView;
import com.maraberto.content.api.CreateContentRequest;
import com.maraberto.content.api.UpdateContentRequest;
import com.maraberto.content.domain.ContentNotFoundException;
import com.maraberto.content.infra.JdbcContentRepository;
import com.maraberto.shared.security.sanitization.SanitizationPolicy;
import com.maraberto.shared.security.sanitization.TextSanitizer;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio do CMS educativo e de divulgação (Fatia 12).
 */
@Service
public class ContentService {

    private final JdbcContentRepository repository;
    private final ApplicationEventPublisher events;
    private final AuditTrail auditTrail;

    public ContentService(
            JdbcContentRepository repository,
            ApplicationEventPublisher events,
            AuditTrail auditTrail) {
        this.repository = repository;
        this.events = events;
        this.auditTrail = auditTrail;
    }

    @Transactional(readOnly = true)
    public List<ContentView> listPublic(ContentKind kind) {
        return repository.listPublic(kind);
    }

    @Transactional(readOnly = true)
    public List<ContentView> listForManagement(ContentKind kind, Boolean published) {
        return repository.listAll(kind, published);
    }

    @Transactional
    public ContentView create(UUID actorId, CreateContentRequest request) {
        validateSemantics(request.kind(), request.youtubeId(), request.externalUrl(), request.externalLinkLabel());

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        int order = request.displayOrder() != null
                ? request.displayOrder()
                : repository.nextDisplayOrder();
        boolean published = Boolean.TRUE.equals(request.published());

        ContentView created = repository.insert(
                id,
                request.kind(),
                request.title(),
                request.description(),
                request.category(),
                normalizeTags(request.tags()),
                blankToNull(request.externalUrl()),
                blankToNull(request.externalLinkLabel()),
                blankToNull(request.youtubeId()),
                blankToNull(request.duration()),
                order,
                published,
                actorId,
                now);

        events.publishEvent(new ContentChanged(id, "CREATED"));
        auditTrail.record(AuditEvent.of(
                actorId,
                AuditActions.CONTENT_CREATED,
                AuditEntityTypes.CONTENT,
                id.toString(),
                Map.of(
                        "kind", created.kind().name(),
                        "published", created.published())));
        return created;
    }

    @Transactional
    public ContentView update(UUID actorId, UUID id, UpdateContentRequest request) {
        validateSemantics(request.kind(), request.youtubeId(), request.externalUrl(), request.externalLinkLabel());

        ContentView existing = repository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException(id));

        int order = request.displayOrder() != null
                ? request.displayOrder()
                : existing.displayOrder();
        boolean published = request.published() != null
                ? request.published()
                : existing.published();

        ContentView updated = repository.update(
                id,
                request.kind(),
                request.title(),
                request.description(),
                request.category(),
                normalizeTags(request.tags()),
                blankToNull(request.externalUrl()),
                blankToNull(request.externalLinkLabel()),
                blankToNull(request.youtubeId()),
                blankToNull(request.duration()),
                order,
                published,
                actorId,
                Instant.now())
                .orElseThrow(() -> new ContentNotFoundException(id));

        events.publishEvent(new ContentChanged(id, "UPDATED"));
        auditTrail.record(AuditEvent.of(
                actorId,
                AuditActions.CONTENT_UPDATED,
                AuditEntityTypes.CONTENT,
                id.toString(),
                Map.of(
                        "kind", updated.kind().name(),
                        "published", updated.published())));
        return updated;
    }

    @Transactional
    public ContentView setPublished(UUID actorId, UUID id, boolean published) {
        ContentView updated = repository.updatePublished(id, published, actorId, Instant.now())
                .orElseThrow(() -> new ContentNotFoundException(id));

        events.publishEvent(new ContentChanged(id, published ? "PUBLISHED" : "UNPUBLISHED"));
        auditTrail.record(AuditEvent.of(
                actorId,
                published ? AuditActions.CONTENT_PUBLISHED : AuditActions.CONTENT_UNPUBLISHED,
                AuditEntityTypes.CONTENT,
                id.toString(),
                Map.of("published", published)));
        return updated;
    }

    @Transactional
    public List<ContentView> reorder(UUID actorId, List<UUID> orderedIds) {
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(orderedIds);
        if (unique.size() != orderedIds.size()) {
            throw new IllegalArgumentException("A lista de reordenação contém IDs duplicados");
        }
        repository.reorder(orderedIds, actorId, Instant.now());

        events.publishEvent(new ContentChanged(orderedIds.getFirst(), "REORDERED"));
        auditTrail.record(AuditEvent.of(
                actorId,
                AuditActions.CONTENT_REORDERED,
                AuditEntityTypes.CONTENT,
                orderedIds.getFirst().toString(),
                Map.of("count", orderedIds.size())));
        return repository.listAll(null, null);
    }

    @Transactional
    public void delete(UUID actorId, UUID id) {
        if (!repository.deleteById(id)) {
            throw new ContentNotFoundException(id);
        }
        events.publishEvent(new ContentChanged(id, "DELETED"));
        auditTrail.record(AuditEvent.of(
                actorId,
                AuditActions.CONTENT_DELETED,
                AuditEntityTypes.CONTENT,
                id.toString(),
                Map.of()));
    }

    private static void validateSemantics(
            ContentKind kind, String youtubeId, String externalUrl, String externalLinkLabel) {
        if (kind == ContentKind.VIDEO && (youtubeId == null || youtubeId.isBlank())) {
            throw new IllegalArgumentException("Vídeos exigem um ID válido do YouTube");
        }
        if (externalLinkLabel != null && !externalLinkLabel.isBlank()
                && (externalUrl == null || externalUrl.isBlank())) {
            throw new IllegalArgumentException("Informe a URL externa junto com o rótulo do link");
        }
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> TextSanitizer.sanitize(tag, SanitizationPolicy.PLAIN_TEXT, 40))
                .filter(tag -> !tag.isBlank())
                .distinct()
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
