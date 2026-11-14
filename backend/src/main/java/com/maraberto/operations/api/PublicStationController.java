package com.maraberto.operations.api;

import com.maraberto.operations.application.StationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Postos fixos ativos e bases móveis com plantão vivo no mapa público
 * (RF01/RF38).
 *
 * <p>Posto desativado não aparece; base móvel só aparece enquanto houver
 * plantão aberto dentro do lease. A coordenada da base é a última posição
 * conhecida do turno, nunca um histórico de rastreamento (RN17).
 */
@RestController
@RequestMapping("/api/v1/stations")
@Tag(name = "Estruturas", description = "Postos fixos e bases móveis visíveis no mapa público")
public class PublicStationController {

    private static final CacheControl CACHE =
            CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic();

    private final StationService stationService;

    public PublicStationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    @Operation(
            summary = "Listar estruturas visíveis",
            description = "Postos fixos ativos e bases móveis com plantão vivo, "
                    + "ordenados por tipo e nome. O cache é curto: desativar um posto "
                    + "ou encerrar o plantão reflete no mapa em até 30 s.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estruturas visíveis",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = PublicStation.class))))
    })
    public ResponseEntity<List<PublicStation>> list() {
        return ResponseEntity.ok()
                .cacheControl(CACHE)
                .body(stationService.publicStations());
    }
}
