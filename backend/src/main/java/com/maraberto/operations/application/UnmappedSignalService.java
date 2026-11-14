package com.maraberto.operations.application;

import com.maraberto.operations.api.LaunchFlagRequest;
import com.maraberto.operations.api.UnmappedSignalDecisionRequest;
import com.maraberto.operations.api.UnmappedSignalView;
import com.maraberto.operations.domain.UnmappedSignalNotFoundException;
import com.maraberto.operations.domain.UnmappedSignalStatus;
import com.maraberto.operations.infra.JdbcUnmappedSignalRepository;
import java.time.Instant;
import java.util.List;
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
 * contém o ponto. Ele **não** é uma bandeira: nada aparece no mapa público e a
 * ficha da praia não muda. A coordenação decide o destino na triagem —
 * identificar numa área existente, pedir um novo mapeamento ou descartar.
 *
 * <p>A área mais próxima é devolvida como sugestão; o vínculo só é gravado com
 * a decisão humana. Uma decisão pode ser revista (inclusive reaberta com
 * {@code PENDING}), porque errar a triagem não pode virar registro imutável.
 */
@Service
public class UnmappedSignalService {

    private static final int MAX_LIST_SIZE = 200;

    private final JdbcUnmappedSignalRepository repository;
    private final NamedParameterJdbcTemplate jdbc;

    public UnmappedSignalService(
            JdbcUnmappedSignalRepository repository,
            NamedParameterJdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    /**
     * Guarda o lançamento que caiu fora do mapeamento.
     *
     * <p>As fotos já foram validadas pelo chamador (existem e são de quem está
     * lançando); aqui só entram na tabela de ligação do registro.
     */
    @Transactional
    public UnmappedSignalView record(
            UUID reportedBy,
            UUID reportedPostId,
            LaunchFlagRequest request,
            List<UUID> mediaIds) {
        UUID id = repository.insert(
                reportedBy,
                reportedPostId,
                request.flag(),
                blankToNull(request.notes()),
                request.latitude(),
                request.longitude(),
                request.accuracyMeters());
        repository.attachMedia(id, mediaIds);
        return repository.findById(id)
                .orElseThrow(UnmappedSignalNotFoundException::new);
    }

    /**
     * Fila de triagem.
     *
     * @param status estado filtrado; {@code null} devolve todos
     * @param limit  máximo de registros (1 a 200)
     */
    @Transactional(readOnly = true)
    public List<UnmappedSignalView> list(UnmappedSignalStatus status, int limit) {
        int size = Math.max(1, Math.min(limit, MAX_LIST_SIZE));
        return repository.findAll(status, size);
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
        repository.findById(signalId).orElseThrow(UnmappedSignalNotFoundException::new);
        validate(request);
        repository.decide(
                signalId,
                request.decision(),
                request.beachId(),
                request.sectorId(),
                blankToNull(request.notes()),
                coordinatorId,
                Instant.now());
        return repository.findById(signalId)
                .orElseThrow(UnmappedSignalNotFoundException::new);
    }

    private void validate(UnmappedSignalDecisionRequest request) {
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
        if (request.sectorId() != null) {
            requireSectorOfBeach(request.beachId(), request.sectorId());
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
