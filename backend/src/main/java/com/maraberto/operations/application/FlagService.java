package com.maraberto.operations.application;

import com.maraberto.media.api.MediaCatalog;
import com.maraberto.operations.api.FlagLaunchResponse;
import com.maraberto.operations.api.FlagResponse;
import com.maraberto.operations.api.LaunchFlagRequest;
import com.maraberto.operations.api.PublicFlag;
import com.maraberto.operations.domain.FlagNotFoundException;
import com.maraberto.operations.infra.JdbcFlagRepository;
import com.maraberto.operations.infra.PublicOperationsRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de vida da sinalização de bandeira (RF12/RN01).
 *
 * <p>O lançamento é append-only e sempre fecha o anterior do mesmo escopo
 * (praia inteira ou setor), sob lock de transação. A vigência é decidida na
 * leitura: {@code closed_at IS NULL AND valid_until > now()}. O histórico não
 * é apagado e o mapa público nunca mostra uma bandeira vencida.
 *
 * <p>O escopo pode ser escolhido no console (praia/setor) ou resolvido a partir
 * do ponto do aparelho. Quando o ponto está dentro do mapeamento, o lançamento
 * guarda a área e o setor: o mapa público precisa de um pin e a ficha precisa
 * de um dono. Quando não está, o registro vai para a triagem da coordenação
 * como área não identificada, sem publicar bandeira (RF12/RN17).
 */
@Service
public class FlagService {

    private final JdbcFlagRepository flags;
    private final PublicOperationsRepository publicRepository;
    private final MediaCatalog mediaCatalog;
    private final UnmappedSignalService unmappedSignals;
    private final NamedParameterJdbcTemplate jdbc;
    private final Duration defaultValidity;
    private final Duration idleTimeout;

    public FlagService(
            JdbcFlagRepository flags,
            PublicOperationsRepository publicRepository,
            MediaCatalog mediaCatalog,
            UnmappedSignalService unmappedSignals,
            NamedParameterJdbcTemplate jdbc,
            @Value("${maraberto.flags.validity:PT12H}") Duration defaultValidity,
            @Value("${maraberto.dispatch.shift-idle-timeout:PT15M}") Duration idleTimeout) {
        this.flags = flags;
        this.publicRepository = publicRepository;
        this.mediaCatalog = mediaCatalog;
        this.unmappedSignals = unmappedSignals;
        this.jdbc = jdbc;
        this.defaultValidity = defaultValidity;
        this.idleTimeout = idleTimeout;
    }

    /**
     * Lança a bandeira e encerra a anterior do mesmo escopo.
     *
     * <p>A validade padrão vem de {@code maraberto.flags.validity} (12 h); o
     * pedido pode encurtar ou estender até 24 h. Um lançamento de setor não
     * apaga a bandeira da praia: o setor passa a ter a sua, e os demais setores
     * continuam herdando a da praia na ficha.
     *
     * <p>Ponto fora do mapeamento não é recusa: o lançamento vira um registro
     * de área não identificada para a coordenação triar (RF12/RN17). Nesse
     * desfecho nenhuma bandeira é publicada no mapa.
     */
    @Transactional
    public FlagLaunchResponse launch(UUID userId, LaunchFlagRequest request) {
        // A foto é validada antes do desfecho: id inexistente ou de outro
        // operador é erro de pedido, não uma sinalização pendente.
        List<UUID> mediaIds = distinctMediaIds(userId, request.mediaIds());
        Optional<Scope> resolved = resolveScope(request);
        UUID postId = currentPostId(userId);

        if (resolved.isEmpty()) {
            return FlagLaunchResponse.unmapped(unmappedSignals.record(
                    userId, postId, request, mediaIds));
        }

        Scope scope = resolved.get();
        Instant now = Instant.now();
        Duration validity = validityOf(request.validityHours());

        flags.lockScope(scope.beachId(), scope.sectorId());
        flags.closeScope(scope.beachId(), scope.sectorId(), userId, now);

        FlagResponse launched = flags.insert(
                scope.beachId(),
                scope.sectorId(),
                postId,
                request.flag(),
                blankToNull(request.notes()),
                userId,
                now,
                now.plus(validity),
                scope.latitude(),
                scope.longitude(),
                scope.accuracyMeters(),
                scope.positionSource());
        flags.attachMedia(launched.id(), mediaIds);

        return FlagLaunchResponse.placed(new FlagResponse(
                launched.id(),
                launched.beachId(),
                launched.sectorId(),
                launched.flag(),
                launched.createdAt(),
                launched.validUntil(),
                launched.closedAt(),
                mediaIds.stream().map(id -> "/api/v1/media/" + id).toList()));
    }

    /** Encerra a sinalização antes da validade; o histórico permanece. */
    @Transactional
    public FlagResponse close(UUID userId, UUID flagId) {
        FlagResponse current = flags.findById(flagId)
                .filter(flag -> flag.closedAt() == null)
                .orElseThrow(FlagNotFoundException::new);
        Instant now = Instant.now();
        flags.close(flagId, userId, now);
        return new FlagResponse(
                current.id(),
                current.beachId(),
                current.sectorId(),
                current.flag(),
                current.createdAt(),
                current.validUntil(),
                now,
                current.photoUrls());
    }

    /**
     * Bandeiras públicas.
     *
     * <p>O mapa pede só as vigentes. A ficha da praia pede o último lançamento
     * de cada escopo, inclusive vencido ou encerrado, para mostrar a última cor
     * conhecida com o selo de desatualizada em vez de esconder a sinalização.
     *
     * @param beachId        área específica; obrigatória quando {@code includeExpired}
     * @param includeExpired inclui o último lançamento mesmo fora da validade
     */
    @Transactional(readOnly = true)
    public List<PublicFlag> publicFlags(UUID beachId, boolean includeExpired) {
        if (!includeExpired) {
            return publicRepository.findActiveFlags(beachId);
        }
        if (beachId == null) {
            throw new IllegalArgumentException(
                    "O histórico de sinalização exige a área de banho (beachId)");
        }
        return publicRepository.findLatestFlags(beachId);
    }

    /**
     * Onde a bandeira está sendo plantada.
     *
     * <p>Com coordenada, o PostGIS resolve o setor (o primeiro da ordem do
     * mapeamento) e, sem setor, a área. Fora do mapeamento a requisição não é
     * recusada: o lançamento vira um registro de área não identificada, porque
     * o ponto pode ser uma praia nova ou um trecho que ainda não entrou no KML.
     * Sem coordenada, praia e setor vêm do console e são validados como antes.
     *
     * @return o escopo mapeado, ou vazio quando o ponto está fora do mapeamento
     */
    private Optional<Scope> resolveScope(LaunchFlagRequest request) {
        if (request.latitude() == null && request.longitude() == null) {
            if (request.beachId() == null) {
                throw new IllegalArgumentException(
                        "Informe a área de banho ou a localização atual do aparelho");
            }
            requireActiveBeach(request.beachId());
            if (request.sectorId() != null) {
                requireSectorOfBeach(request.beachId(), request.sectorId());
            }
            return Optional.of(new Scope(request.beachId(), request.sectorId(),
                    null, null, null, "SCOPE"));
        }
        if (request.latitude() == null || request.longitude() == null) {
            throw new IllegalArgumentException(
                    "Informe latitude e longitude juntas, ou escolha a praia e o setor");
        }

        Optional<ResolvedSector> sector = jdbc.query("""
                        SELECT s.id, s.beach_id
                          FROM beach_sectors s
                          JOIN beaches b ON b.id = s.beach_id AND b.active
                         WHERE s.active
                           AND ST_Contains(s.geometry,
                                   CAST(ST_SetSRID(ST_MakePoint(
                                           CAST(:longitude AS double precision),
                                           CAST(:latitude AS double precision)), 4326)
                                       AS geometry))
                         ORDER BY s.display_order, s.id
                         LIMIT 1
                        """,
                pointParameters(request.latitude(), request.longitude()),
                (rs, rowNum) -> new ResolvedSector(
                        rs.getObject("id", UUID.class),
                        rs.getObject("beach_id", UUID.class))).stream().findFirst();
        if (sector.isPresent()) {
            return Optional.of(new Scope(sector.get().beachId(), sector.get().sectorId(),
                    request.latitude(), request.longitude(),
                    request.accuracyMeters(), "DEVICE_GPS"));
        }

        Optional<UUID> area = jdbc.query("""
                        SELECT b.id
                          FROM beaches b
                         WHERE b.active
                           AND ST_Contains(b.boundary,
                                   CAST(ST_SetSRID(ST_MakePoint(
                                           CAST(:longitude AS double precision),
                                           CAST(:latitude AS double precision)), 4326)
                                       AS geometry))
                         ORDER BY b.id
                         LIMIT 1
                        """,
                pointParameters(request.latitude(), request.longitude()),
                (rs, rowNum) -> rs.getObject("id", UUID.class)).stream().findFirst();
        return area.map(beachId -> new Scope(beachId, null,
                request.latitude(), request.longitude(),
                request.accuracyMeters(), "DEVICE_GPS"));
    }

    /**
     * Fotos do lançamento: precisam existir e ter sido enviadas pelo mesmo
     * operador. Repetir o mesmo id não duplica a linha em {@code flag_media}.
     */
    private List<UUID> distinctMediaIds(UUID userId, List<UUID> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        Set<UUID> distinct = new LinkedHashSet<>(requested);
        Set<UUID> owned = mediaCatalog.findOwned(userId, distinct);
        if (!owned.containsAll(distinct)) {
            throw new IllegalArgumentException(
                    "Uma das fotos não existe ou foi enviada por outro operador");
        }
        return List.copyOf(distinct);
    }

    private static MapSqlParameterSource pointParameters(double latitude, double longitude) {
        return new MapSqlParameterSource()
                .addValue("latitude", latitude)
                .addValue("longitude", longitude);
    }

    /**
     * Posto do plantão vivo do operador, quando houver. A bandeira guarda a
     * origem para a ficha; base móvel não tem posto e fica sem nome.
     */
    private UUID currentPostId(UUID userId) {
        return jdbc.query("""
                        SELECT post_id
                          FROM shift_sessions
                         WHERE user_id = :userId
                           AND ended_at IS NULL
                           AND post_id IS NOT NULL
                           AND last_seen_at >= NOW() - make_interval(secs => :idleSeconds)
                        """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("idleSeconds", idleTimeout.toSeconds()),
                rs -> rs.next() ? rs.getObject("post_id", UUID.class) : null);
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

    private Duration validityOf(Double hours) {
        if (hours == null) {
            return defaultValidity;
        }
        Duration requested = Duration.ofSeconds(Math.round(hours * 3600));
        Duration ceiling = Duration.ofHours(24);
        return requested.compareTo(ceiling) > 0 ? ceiling : requested;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Área, setor e ponto que o lançamento vai guardar. */
    private record Scope(
            UUID beachId,
            UUID sectorId,
            Double latitude,
            Double longitude,
            Double accuracyMeters,
            String positionSource) {
    }

    private record ResolvedSector(UUID sectorId, UUID beachId) {
    }
}
