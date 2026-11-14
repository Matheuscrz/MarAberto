package com.maraberto.operations.api;

import com.maraberto.operations.domain.StationKind;
import com.maraberto.operations.domain.StationPositionSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Estrutura operacional no painel, ativa ou não.
 *
 * <p>O histórico de plantões e ocorrências continua apontando para a linha
 * desativada; {@code deactivatedAt} e {@code statusNote} explicam por que ela
 * saiu do mapa. A origem da posição diz se o ponto veio do aparelho em campo
 * ({@code DEVICE_GPS}), do cadastro antigo ({@code MANUAL}) ou do mapa
 * ({@code MAP_PICK}), com a precisão informada.
 */
@Schema(name = "AdminStation", description = "Posto fixo ou base móvel com o estado de ativação")
public record StationAdminView(
        UUID id,
        StationKind kind,
        String name,
        UUID beachId,
        String beachName,
        double latitude,
        double longitude,
        StationPositionSource positionSource,
        Double positionAccuracyM,
        Instant positionUpdatedAt,
        boolean active,
        Instant deactivatedAt,
        String statusNote,
        List<String> photoUrls) {
}
