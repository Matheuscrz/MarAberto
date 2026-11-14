package com.maraberto.operations.api;

import com.maraberto.operations.domain.StationKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Posto fixo ativo ou base móvel viva no mapa público (RF01/RF38).
 *
 * <p>Base móvel só entra quando existe plantão aberto com posição recente; a
 * coordenada é a última posição conhecida do turno, nunca um histórico de
 * rastreamento (RN17). Posto desativado não aparece em nenhuma tela pública.
 *
 * @param id                identificador da estrutura
 * @param kind              posto fixo ou base móvel
 * @param name              nome exibido no pin
 * @param beachName         praia do posto fixo; nulo para base móvel
 * @param latitude          latitude WGS84 usada no pin
 * @param longitude         longitude WGS84 usada no pin
 * @param positionUpdatedAt quando a posição foi conhecida (base móvel); nulo no posto fixo
 * @param photoUrls         fotos exibidas na ficha pública do posto/base
 */
@Schema(name = "PublicStation", description = "Posto fixo ativo ou base móvel com plantão vivo")
public record PublicStation(
        UUID id,
        StationKind kind,
        String name,
        String beachName,
        double latitude,
        double longitude,
        Instant positionUpdatedAt,
        List<String> photoUrls) {
}
