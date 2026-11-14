package com.maraberto.beaches.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.maraberto.beaches.application.RiskZoneQueryService;
import com.maraberto.shared.api.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Zonas de risco visíveis no mapa público (RF02/RF18).
 *
 * <p>Zona permanente (foz de rio, costão, píer) nunca expira; zona
 * semipermanente some ao vencer. A resposta é GeoJSON pronto para a camada do
 * mapa, com a cor por tipo de risco.
 */
@RestController
@RequestMapping("/api/v1/risk-zones")
@Tag(name = "Riscos", description = "Zonas de risco delimitadas no mapa")
public class PublicRiskZoneController {

    private static final CacheControl CACHE =
            CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

    private final RiskZoneQueryService queryService;

    public PublicRiskZoneController(RiskZoneQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(
            summary = "Listar zonas de risco visíveis",
            description = "Devolve uma FeatureCollection GeoJSON com as zonas ativas e dentro "
                    + "da validade. O retângulo é opcional e precisa dos quatro cantos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "FeatureCollection de zonas visíveis"),
            @ApiResponse(responseCode = "400", description = "Retângulo incompleto ou inválido (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<JsonNode> list(
            @Parameter(description = "Longitude oeste do retângulo", example = "-38.2")
            @RequestParam(required = false) Double west,
            @Parameter(description = "Latitude sul do retângulo", example = "-11.5")
            @RequestParam(required = false) Double south,
            @Parameter(description = "Longitude leste do retângulo", example = "-36.4")
            @RequestParam(required = false) Double east,
            @Parameter(description = "Latitude norte do retângulo", example = "-9.5")
            @RequestParam(required = false) Double north) {
        return ResponseEntity.ok()
                .cacheControl(CACHE)
                .body(queryService.visible(west, south, east, north));
    }
}
