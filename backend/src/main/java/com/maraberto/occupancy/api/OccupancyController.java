package com.maraberto.occupancy.api;

import com.maraberto.occupancy.application.BeachOccupancy;
import com.maraberto.occupancy.application.OccupancyEstimate;
import com.maraberto.occupancy.application.OccupancyService;
import com.maraberto.shared.api.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Indicador de ocupação anônimo e aproximado (RF10).
 *
 * <p>A posição só é usada para achar o setor: o servidor não devolve nem grava
 * coordenada. Abaixo da amostra mínima a resposta é "dados insuficientes"
 * (RN06), o que também protege a privacidade de praias vazias.
 */
@RestController
@RequestMapping("/api/v1/occupancy")
@Tag(name = "Ocupação", description = "Indicador anônimo de ocupação por área de banho")
public class OccupancyController {

    private final OccupancyService service;

    public OccupancyController(OccupancyService service) {
        this.service = service;
    }

    @PostMapping("/samples")
    @Operation(
            summary = "Registrar presença anônima",
            description = "Resolve o setor pela posição, grava uma amostra anônima e devolve a "
                    + "faixa atualizada. Fora de uma área mapeada responde 204 sem gravar nada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Amostra registrada"),
            @ApiResponse(responseCode = "204", description = "Fora de uma área de banho mapeada"),
            @ApiResponse(responseCode = "400", description = "Amostra inválida (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<OccupancyEstimate> sample(
            @Valid @RequestBody OccupancySampleRequest request) {
        return service.record(request.latitude(), request.longitude(), request.token())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/beaches/{beachId}")
    @Operation(
            summary = "Ocupação da área e dos setores",
            description = "Janela móvel de 15 min; sem amostra mínima devolve dados insuficientes "
                    + "na área e no setor. A faixa da área é a união das amostras, nunca a soma "
                    + "das faixas por setor: quem está na fronteira conta nos dois setores.")
    @ApiResponse(responseCode = "200", description = "Faixa de ocupação atual")
    public BeachOccupancy estimate(@PathVariable UUID beachId) {
        return service.occupancy(beachId);
    }
}
