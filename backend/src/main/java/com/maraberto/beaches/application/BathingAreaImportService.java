package com.maraberto.beaches.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.beaches.api.BathingAreaMapChanged;
import com.maraberto.beaches.api.BeachChanged;
import com.maraberto.beaches.api.SectorChanged;
import com.maraberto.beaches.domain.BeachKind;
import com.maraberto.beaches.infra.BathingAreaRepository;
import com.maraberto.beaches.infra.BathingAreaRepository.AreaRef;
import com.maraberto.beaches.infra.BathingAreaRepository.AreaWrite;
import com.maraberto.beaches.infra.BathingAreaRepository.BatchWrite;
import com.maraberto.beaches.infra.BathingAreaRepository.ExistingSector;
import com.maraberto.beaches.infra.BathingAreaRepository.SectorRef;
import com.maraberto.beaches.infra.BathingAreaRepository.SectorWrite;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.locationtech.jts.geom.MultiPolygon;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importa e atualiza o mapa de áreas de banho a partir do KML (ADR-016).
 *
 * <p>O arquivo pode ser o mesmo reexportado do QGIS: o upsert é por
 * {@code external_id} e preserva o UUID da área e do setor. {@code UPSERT} nunca
 * remove; {@code SYNC} desativa (soft delete) o que sumiu do arquivo.
 */
@Service
public class BathingAreaImportService {

    private static final String SOURCE = "qgis-kml";
    private static final Pattern STATE_PATTERN = Pattern.compile("[A-Z]{2}");
    private static final double MIN_LON = -74.1;
    private static final double MAX_LON = -34.7;
    private static final double MIN_LAT = -33.8;
    private static final double MAX_LAT = 5.4;

    private final KmlFeatureReader reader;
    private final BathingAreaRepository repository;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    public BathingAreaImportService(
            KmlFeatureReader reader,
            BathingAreaRepository repository,
            ApplicationEventPublisher events,
            ObjectMapper objectMapper) {
        this.reader = reader;
        this.repository = repository;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImportReport importKml(ImportCommand command) {
        String state = normalizeState(command.state());
        String prefix = state.toLowerCase(Locale.ROOT) + "-areas-banho:";
        String sourceVersion = GeometryHasher.sha256(command.content());

        List<ImportedFeature> features = reader.read(new ByteArrayInputStream(command.content()));
        List<FeatureChange> changes = new ArrayList<>();
        List<ImportedFeature> accepted = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (ImportedFeature feature : features) {
            if (feature.uf() != null && !feature.uf().equalsIgnoreCase(state)) {
                throw new ImportRejectedException(
                        "A feição " + feature.id() + " declara UF " + feature.uf()
                                + ", diferente da UF informada (" + state + ")");
            }
            if (!insideBrazil(feature.geometry())) {
                changes.add(FeatureChange.rejected(
                        prefix + feature.id(), feature.praia(), feature.setor(),
                        feature.municipio(), "Geometria fora dos limites do Brasil"));
                continue;
            }
            if (!seenIds.add(feature.id())) {
                changes.add(FeatureChange.rejected(
                        prefix + feature.id(), feature.praia(), feature.setor(),
                        feature.municipio(), "external_id duplicado no arquivo"));
                continue;
            }
            accepted.add(feature);
        }
        if (accepted.isEmpty()) {
            throw new ImportRejectedException("Nenhuma feição válida para importar");
        }

        Map<AreaKey, AreaGroup> groups = group(accepted, state);
        Map<String, AreaRef> areasByExternal = new HashMap<>();
        Map<String, AreaRef> areasByKey = new HashMap<>();
        for (AreaRef area : repository.findAreas(state)) {
            if (area.externalId() != null) {
                areasByExternal.put(area.externalId(), area);
            }
            areasByKey.put(key(area.municipality(), area.name()), area);
        }

        Map<String, ExistingSector> existingSectors = repository.findSectors(
                accepted.stream().map(feature -> prefix + feature.id()).toList());

        Set<String> incoming = accepted.stream()
                .map(feature -> prefix + feature.id())
                .collect(java.util.stream.Collectors.toSet());
        List<FeatureChange> missing = new ArrayList<>();
        for (SectorRef sector : repository.findSectorsByPrefix(prefix)) {
            if (sector.active() && !incoming.contains(sector.externalId())) {
                missing.add(FeatureChange.of(
                        sector.externalId(), null, null, sector.name(), null,
                        FeatureAction.MISSING, List.of()));
            }
        }

        // Reenviar exatamente o arquivo do último lote aplicado não muda nada:
        // o parser e a validação continuam rodando, mas as linhas e a projeção
        // do mapa não são reescritas (ADR-016). Só vale quando nada foi
        // rejeitado e não há feição ativa fora do arquivo.
        if (!command.dryRun()
                && changes.isEmpty()
                && missing.isEmpty()
                && repository.lastAppliedSourceVersion(state)
                        .filter(sourceVersion::equals)
                        .isPresent()) {
            return unchangedReport(command, prefix, state, sourceVersion, groups);
        }

        Set<UUID> affectedAreas = new HashSet<>();
        List<SectorChanged> sectorEvents = new ArrayList<>();

        for (AreaGroup group : groups.values()) {
            ImportedFeature anchor = group.features().getFirst();
            String areaExternalId = prefix + "area:" + anchor.id();
            AreaRef existing = areasByExternal.get(areaExternalId);
            if (existing == null) {
                existing = areasByKey.get(key(group.municipality(), group.name()));
            }

            List<String> sectorHashes = group.features().stream()
                    .map(feature -> GeometryHasher.geometryHash(feature.geometry()))
                    .sorted()
                    .toList();
            AreaWrite areaWrite = new AreaWrite(
                    areaExternalId,
                    group.name(),
                    group.municipality(),
                    state,
                    inferKind(group.name()),
                    GeometryHasher.sha256(String.join(",", sectorHashes)),
                    SOURCE,
                    sourceVersion,
                    metadataJson(anchor),
                    command.importedBy());

            UUID beachId = existing == null ? null : existing.id();
            if (!command.dryRun()) {
                if (existing == null) {
                    beachId = repository.insertArea(areaWrite);
                } else {
                    repository.updateArea(existing.id(), areaWrite);
                }
                affectedAreas.add(beachId);
            }

            int order = 1;
            for (ImportedFeature feature : group.features()) {
                String sectorExternalId = prefix + feature.id();
                String hash = GeometryHasher.geometryHash(feature.geometry());
                ExistingSector current = existingSectors.get(sectorExternalId);
                List<String> changedFields = changedFields(current, feature, order, hash, beachId);

                FeatureAction action;
                if (current == null) {
                    action = FeatureAction.CREATED;
                } else if (changedFields.isEmpty()) {
                    action = FeatureAction.UNCHANGED;
                } else {
                    action = FeatureAction.UPDATED;
                }
                changes.add(FeatureChange.of(
                        sectorExternalId, areaExternalId, group.name(), feature.setor(),
                        group.municipality(), action, changedFields));

                if (!command.dryRun() && action != FeatureAction.UNCHANGED) {
                    repository.upsertSector(new SectorWrite(
                            beachId,
                            sectorExternalId,
                            feature.setor(),
                            order,
                            feature.geometry(),
                            hash,
                            SOURCE,
                            sourceVersion,
                            metadataJson(feature),
                            command.importedBy()));
                    sectorEvents.add(new SectorChanged(beachId, sectorExternalId));
                }
                order++;
            }
        }

        if (!command.dryRun()) {
            if (command.mode() == ImportMode.SYNC && !missing.isEmpty()) {
                List<String> missingIds = missing.stream().map(FeatureChange::externalId).toList();
                repository.deactivateSectors(missingIds);
                for (SectorRef sector : repository.findSectorsByPrefix(prefix)) {
                    if (missingIds.contains(sector.externalId())) {
                        affectedAreas.add(sector.beachId());
                    }
                }
            }
            for (UUID beachId : affectedAreas) {
                repository.recomputeBoundary(beachId);
            }
        }

        ImportReport report = buildReport(
                command, state, sourceVersion, changes, missing);

        if (!command.dryRun()) {
            repository.insertBatch(new BatchWrite(
                    command.sourceFile(),
                    sourceVersion,
                    state,
                    command.mode().name(),
                    false,
                    true,
                    report.created(),
                    report.updated(),
                    report.unchanged(),
                    report.missing(),
                    report.rejected(),
                    command.importedBy(),
                    toJson(report)));
            for (UUID beachId : affectedAreas) {
                events.publishEvent(new BeachChanged(beachId));
            }
            for (SectorChanged event : sectorEvents) {
                events.publishEvent(event);
            }
            events.publishEvent(new BathingAreaMapChanged(state));
        }

        return report;
    }

    private ImportReport buildReport(
            ImportCommand command,
            String state,
            String sourceVersion,
            List<FeatureChange> changes,
            List<FeatureChange> missing) {
        int created = count(changes, FeatureAction.CREATED);
        int updated = count(changes, FeatureAction.UPDATED);
        int unchanged = count(changes, FeatureAction.UNCHANGED);
        int rejected = count(changes, FeatureAction.REJECTED);
        return new ImportReport(
                command.sourceFile(),
                sourceVersion,
                state,
                command.mode(),
                command.dryRun(),
                !command.dryRun(),
                created,
                updated,
                unchanged,
                missing.size(),
                rejected,
                List.copyOf(changes));
    }

    /**
     * Relatório do arquivo idêntico ao último lote aplicado: tudo inalterado e
     * nada escrito. `applied` fica falso de propósito — o mapa já era este.
     */
    private ImportReport unchangedReport(
            ImportCommand command,
            String prefix,
            String state,
            String sourceVersion,
            Map<AreaKey, AreaGroup> groups) {
        List<FeatureChange> changes = new ArrayList<>();
        for (AreaGroup group : groups.values()) {
            String areaExternalId = prefix + "area:" + group.features().getFirst().id();
            for (ImportedFeature feature : group.features()) {
                changes.add(FeatureChange.of(
                        prefix + feature.id(),
                        areaExternalId,
                        group.name(),
                        feature.setor(),
                        group.municipality(),
                        FeatureAction.UNCHANGED,
                        List.of()));
            }
        }
        return new ImportReport(
                command.sourceFile(),
                sourceVersion,
                state,
                command.mode(),
                command.dryRun(),
                false,
                0,
                0,
                changes.size(),
                0,
                0,
                List.copyOf(changes));
    }

    private static int count(List<FeatureChange> changes, FeatureAction action) {
        return (int) changes.stream().filter(change -> change.action() == action).count();
    }

    private static List<String> changedFields(
            ExistingSector current,
            ImportedFeature feature,
            int order,
            String hash,
            UUID beachId) {
        if (current == null) {
            return List.of();
        }
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(current.name(), feature.setor())) {
            changed.add("name");
        }
        if (current.displayOrder() != order) {
            changed.add("display_order");
        }
        if (!Objects.equals(current.geometryHash(), hash)) {
            changed.add("geometry");
        }
        if (beachId != null && !Objects.equals(current.beachId(), beachId)) {
            changed.add("beach_id");
        }
        if (!current.active()) {
            changed.add("active");
        }
        return changed;
    }

    private static Map<AreaKey, AreaGroup> group(List<ImportedFeature> features, String state) {
        Map<AreaKey, List<ImportedFeature>> grouped = new LinkedHashMap<>();
        for (ImportedFeature feature : features) {
            AreaKey key = new AreaKey(
                    state,
                    NameNormalizer.normalize(feature.municipio()),
                    NameNormalizer.normalize(feature.praia()));
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(feature);
        }

        Map<AreaKey, AreaGroup> groups = new LinkedHashMap<>();
        grouped.forEach((key, items) -> {
            List<ImportedFeature> ordered = items.stream()
                    .sorted(Comparator.comparingLong(BathingAreaImportService::numericId)
                            .thenComparing(ImportedFeature::id))
                    .toList();
            ImportedFeature first = ordered.getFirst();
            groups.put(key, new AreaGroup(
                    key, first.municipio(), first.praia(), ordered));
        });
        return groups;
    }

    private static long numericId(ImportedFeature feature) {
        try {
            return Long.parseLong(feature.id());
        } catch (NumberFormatException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static String key(String municipality, String name) {
        return NameNormalizer.normalize(municipality) + "|" + NameNormalizer.normalize(name);
    }

    private static BeachKind inferKind(String areaName) {
        String normalized = NameNormalizer.normalize(areaName);
        if (normalized.startsWith("barragem") || normalized.startsWith("acude")) {
            return BeachKind.DAM;
        }
        return BeachKind.BEACH;
    }

    private static boolean insideBrazil(MultiPolygon geometry) {
        var envelope = geometry.getEnvelopeInternal();
        return envelope.getMinX() >= MIN_LON
                && envelope.getMaxX() <= MAX_LON
                && envelope.getMinY() >= MIN_LAT
                && envelope.getMaxY() <= MAX_LAT;
    }

    private static String normalizeState(String state) {
        String normalized = state == null ? "" : state.trim().toUpperCase(Locale.ROOT);
        if (!STATE_PATTERN.matcher(normalized).matches()) {
            throw new ImportRejectedException("UF inválida: informe a sigla com duas letras");
        }
        return normalized;
    }

    private String metadataJson(ImportedFeature feature) {
        try {
            return objectMapper.writeValueAsString(feature.metadata());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Falha ao serializar proveniência", exception);
        }
    }

    private String toJson(ImportReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Falha ao serializar relatório", exception);
        }
    }

    private record AreaKey(String state, String municipality, String name) {
    }

    private record AreaGroup(
            AreaKey key,
            String municipality,
            String name,
            List<ImportedFeature> features) {
    }

    /** Entrada já lida do multipart/configuração. */
    public record ImportCommand(
            byte[] content,
            String sourceFile,
            String state,
            boolean dryRun,
            ImportMode mode,
            UUID importedBy) {
    }
}
