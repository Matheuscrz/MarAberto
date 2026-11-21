package com.maraberto.operations.api;

import com.maraberto.operations.domain.FlagColor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Alerta público de sinalização lançada fora do mapeamento (RF12/RN17).
 *
 * <p>O ponto pode ser uma praia nova, um trecho de rio sem KML ou uma barragem
 * que passou a receber banhistas. Enquanto a coordenação não identifica o
 * local, o banhista vê o pin com a cor lançada e o aviso de que a área não
 * está mapeada — o alerta não espera a triagem para existir.
 *
 * <p>Não é uma bandeira de praia: não existe {@code beachId}, setor, herança
 * por setor nem ficha de área. A vigência é a mesma das bandeiras e é decidida
 * no servidor ({@code valid_until > now()}); a triagem continua na fila da
 * coordenação mesmo depois de o alerta vencer.
 *
 * @param id              identificador do registro de triagem
 * @param flag            cor lançada em campo
 * @param notes           observação do guarda-vidas, quando houver
 * @param latitude        latitude do ponto do lançamento
 * @param longitude       longitude do ponto do lançamento
 * @param accuracyMeters  precisão informada pelo aparelho
 * @param createdAt       quando o alerta foi lançado
 * @param validUntil      fim da validade; depois disso o pin sai do mapa
 * @param photoUrls       fotos do lançamento, na ordem de exibição
 */
@Schema(name = "PublicUnmappedSignal",
        description = "Alerta de sinalização em área ainda não mapeada")
public record PublicUnmappedSignal(
        UUID id,
        FlagColor flag,
        String notes,
        double latitude,
        double longitude,
        Double accuracyMeters,
        Instant createdAt,
        Instant validUntil,
        @Schema(description = "Fotos do lançamento") List<String> photoUrls) {

    public PublicUnmappedSignal {
        photoUrls = photoUrls == null ? List.of() : List.copyOf(photoUrls);
    }
}
