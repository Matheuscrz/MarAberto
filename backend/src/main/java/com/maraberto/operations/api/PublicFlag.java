package com.maraberto.operations.api;

import com.maraberto.operations.domain.FlagColor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sinalização vigente no mapa público (RF12/RF38).
 *
 * <p>Só aparece o lançamento não encerrado e dentro da validade (RN01); a
 * bandeira vencida continua no histórico, mas sai do mapa. A posição do pin é
 * o ponto enviado pelo aparelho quando a sinalização foi lançada por
 * localização, o centroide do setor quando é de setor, ou um ponto interno da
 * área quando é da praia inteira. O deslocamento do guarda-vidas nunca vira
 * pin — só o ponto do lançamento.
 *
 * @param id          identificador do lançamento
 * @param beachId     área de banho sinalizada
 * @param beachName   nome da área de banho
 * @param sectorId    setor sinalizado; nulo quando a bandeira vale para a praia
 * @param sectorLabel rótulo pronto ("Setor 3" quando o mapeamento não nomeia)
 * @param flag        cor vigente
 * @param notes       observação do guarda-vidas, quando houver
 * @param latitude    latitude do pin
 * @param longitude   longitude do pin
 * @param stationName posto que lançou a bandeira, quando identificado
 * @param postedAt    quando a bandeira foi lançada
 * @param validUntil  fim da validade; depois disso a sinalização é "desatualizada"
 * @param closedAt    quando foi encerrada antes da validade; nulo enquanto vigente
 * @param photoUrls   fotos do lançamento, na ordem de exibição
 */
@Schema(name = "PublicFlag", description = "Sinalização vigente de uma área de banho ou setor")
public record PublicFlag(
        UUID id,
        UUID beachId,
        String beachName,
        UUID sectorId,
        String sectorLabel,
        FlagColor flag,
        String notes,
        double latitude,
        double longitude,
        String stationName,
        Instant postedAt,
        Instant validUntil,
        Instant closedAt,
        @Schema(description = "Fotos do lançamento") List<String> photoUrls) {

    public PublicFlag {
        photoUrls = photoUrls == null ? List.of() : List.copyOf(photoUrls);
    }

    /** Vigente no mapa: nem encerrada, nem vencida. */
    public boolean active() {
        return closedAt == null && validUntil.isAfter(Instant.now());
    }
}
