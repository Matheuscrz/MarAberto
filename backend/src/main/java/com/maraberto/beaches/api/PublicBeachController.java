package com.maraberto.beaches.api;

import com.maraberto.beaches.application.BeachMapQueryService;
import com.maraberto.shared.api.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mapa público de áreas de banho (RF01/RF04/RF18).
 *
 * <p>A lista é sempre por proximidade: sem posição não há lista. O raio é
 * limitado a 30 km para o banhista ver o que dá para alcançar, não o estado
 * inteiro. A praia é acessada pela lista e pela ficha; ela não tem marcador
 * próprio no mapa.
 */
@RestController
@RequestMapping("/api/v1/beaches")
@Tag(name = "Praias", description = "Áreas de banho importadas do mapeamento do CBM")
public class PublicBeachController {

    private final BeachMapQueryService queryService;

    public PublicBeachController(BeachMapQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(
            summary = "Listar áreas de banho próximas",
            description = "Consulta as áreas ativas num raio de até 30 km da posição informada, "
                    + "da mais próxima para a mais distante. A posição não é armazenada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Áreas dentro do raio"),
            @ApiResponse(responseCode = "400", description = "Posição ou raio inválidos (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<NearbyBeaches> nearby(
            @Parameter(description = "Latitude de quem consulta", example = "-10.985")
            @RequestParam(required = false) Double lat,
            @Parameter(description = "Longitude de quem consulta", example = "-37.045")
            @RequestParam(required = false) Double lon,
            @Parameter(description = "Raio em metros, no máximo 30000", example = "30000")
            @RequestParam(defaultValue = "30000") Integer radiusM) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePrivate())
                .body(queryService.nearby(lat, lon, radiusM));
    }

    @GetMapping("/{beachId}")
    @Operation(
            summary = "Ficha pública da área de banho",
            description = "Devolve a área e os setores ativos na ordem do mapeamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Área encontrada"),
            @ApiResponse(responseCode = "404", description = "Área inexistente (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public BeachDetail detail(@PathVariable UUID beachId) {
        return queryService.detail(beachId);
    }
}
