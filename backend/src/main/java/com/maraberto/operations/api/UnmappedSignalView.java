package com.maraberto.operations.api;

import com.maraberto.operations.domain.FlagColor;
import com.maraberto.operations.domain.UnmappedSignalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sinalização registrada fora do mapeamento (RF12/RN17).
 *
 * <p>É o que o guarda-vidas vê depois de lançar a bandeira num ponto que o
 * KML não cobre: o registro existe e foi para a coordenação, mas **nada é
 * publicado no mapa** — não há praia nem setor para o pin. A coordenação
 * identifica o local (associando a uma área existente), pede um novo
 * mapeamento ou descarta o registro.
 *
 * <p>A coordenada é a do lançamento, não um rastro do aparelho. A área mais
 * próxima é uma sugestão de triagem calculada na consulta, nunca um vínculo
 * automático: a decisão de qual área é aquela continua humana.
 *
 * @param id                        identificador do registro
 * @param flag                      cor que o operador queria lançar
 * @param notes                     observação de campo
 * @param latitude                  latitude do ponto do lançamento
 * @param longitude                 longitude do ponto do lançamento
 * @param accuracyMeters            precisão informada pelo aparelho
 * @param reportedByBadge           matrícula de quem reportou
 * @param reportedPostId            posto do plantão de quem reportou, quando havia
 * @param reportedPostName          nome do posto do plantão
 * @param status                    estado da triagem
 * @param beachId                   área associada na decisão {@code IDENTIFIED}
 * @param beachName                 nome da área associada
 * @param sectorId                  setor associado; nulo quando a área inteira
 * @param sectorLabel               rótulo pronto do setor ("Setor N" sem nome)
 * @param nearestBeachId            área ativa mais próxima do ponto (sugestão)
 * @param nearestBeachName          nome da área mais próxima
 * @param nearestBeachDistanceMeters distância até a área mais próxima, em metros
 * @param decisionNotes             justificativa da coordenação
 * @param createdAt                 quando o registro entrou na fila
 * @param decidedAt                 quando a coordenação decidiu; nulo se pendente
 * @param photoUrls                 fotos do registro, na ordem de exibição
 */
@Schema(name = "UnmappedSignal",
        description = "Registro de sinalização fora do mapeamento, em triagem pela coordenação")
public record UnmappedSignalView(
        UUID id,
        FlagColor flag,
        String notes,
        double latitude,
        double longitude,
        Double accuracyMeters,
        @Schema(description = "Matrícula de quem registrou") String reportedByBadge,
        UUID reportedPostId,
        String reportedPostName,
        UnmappedSignalStatus status,
        @Schema(description = "Área associada na identificação; nula enquanto pendente")
        UUID beachId,
        String beachName,
        UUID sectorId,
        String sectorLabel,
        @Schema(description = "Área ativa mais próxima do ponto, sugerida para a triagem")
        UUID nearestBeachId,
        String nearestBeachName,
        Long nearestBeachDistanceMeters,
        String decisionNotes,
        Instant createdAt,
        Instant decidedAt,
        @Schema(description = "Fotos do registro") List<String> photoUrls) {

    public UnmappedSignalView {
        photoUrls = photoUrls == null ? List.of() : List.copyOf(photoUrls);
    }
}
