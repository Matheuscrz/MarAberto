package com.maraberto.occupancy.application;

import com.maraberto.occupancy.domain.OccupancyLevel;
import com.maraberto.occupancy.domain.OccupancyStatus;
import com.maraberto.occupancy.infra.OccupancyRepository;
import com.maraberto.shared.security.crypto.BlindIndexer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicador de ocupação anônimo (RF10/RN06/RNF04).
 *
 * <p>O caminho é: posição atual → setor que a contém → amostra anônima com o
 * HMAC de um token efêmero. A coordenada é descartada ali; o banco guarda
 * apenas setor, janela de 15 min e HMAC. A janela é móvel e as amostras velhas
 * são apagadas na escrita, então não sobra trilha para reconstruir circulação.
 *
 * <p>O número de amostras **não** sai da API: abaixo do mínimo a resposta é
 * "dados insuficientes"; acima, apenas a faixa.
 */
@Service
public class OccupancyService {

    private static final long BUCKET_SECONDS = 900;

    private final OccupancyRepository repository;
    private final BlindIndexer blindIndexer;
    private final OccupancyProperties properties;

    public OccupancyService(
            OccupancyRepository repository,
            BlindIndexer blindIndexer,
            OccupancyProperties properties) {
        this.repository = repository;
        this.blindIndexer = blindIndexer;
        this.properties = properties;
    }

    /**
     * Registra a presença do aparelho, se ele estiver dentro de um setor ativo.
     *
     * @return a estimativa atualizada, ou vazio quando o ponto não é uma área
     *         de banho mapeada (nada é gravado nesse caso)
     */
    @Transactional
    public Optional<OccupancyEstimate> record(double latitude, double longitude, String token) {
        Optional<OccupancyRepository.SectorLocation> sector =
                repository.findSectorAt(latitude, longitude);
        if (sector.isEmpty()) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        repository.insertSample(
                sector.get().beachId(),
                sector.get().sectorId(),
                bucketStart(now),
                blindIndexer.index(token));
        repository.purgeOlderThan(
                now.minus(properties.getRetention()).atOffset(ZoneOffset.UTC));

        return Optional.of(estimate(sector.get().beachId(), now));
    }

    public OccupancyEstimate estimate(UUID beachId) {
        return estimate(beachId, Instant.now());
    }

    private OccupancyEstimate estimate(UUID beachId, Instant now) {
        int samples = repository.countDistinctTokens(
                beachId,
                now.minus(properties.getWindow()).atOffset(ZoneOffset.UTC));
        int windowMinutes = (int) properties.getWindow().toMinutes();

        if (samples < properties.getMinimumSample()) {
            return new OccupancyEstimate(
                    beachId, OccupancyStatus.INSUFFICIENT, null, windowMinutes);
        }
        return new OccupancyEstimate(
                beachId, OccupancyStatus.ESTIMATE, level(samples), windowMinutes);
    }

    private OccupancyLevel level(int samples) {
        if (samples < properties.getModerateThreshold()) {
            return OccupancyLevel.LOW;
        }
        if (samples < properties.getHighThreshold()) {
            return OccupancyLevel.MODERATE;
        }
        if (samples < properties.getExtremeThreshold()) {
            return OccupancyLevel.HIGH;
        }
        return OccupancyLevel.EXTREME;
    }

    /** Início da janela de 15 min em que a amostra cai (UTC). */
    private static OffsetDateTime bucketStart(Instant now) {
        long epochSeconds = now.getEpochSecond();
        return Instant.ofEpochSecond(epochSeconds - epochSeconds % BUCKET_SECONDS)
                .atOffset(ZoneOffset.UTC);
    }
}
