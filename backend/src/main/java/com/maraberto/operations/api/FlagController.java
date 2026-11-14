package com.maraberto.operations.api;

import com.maraberto.operations.application.FlagService;
import com.maraberto.shared.api.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lançamento e encerramento de sinalização pelo guarda-vidas e pela
 * coordenação (RF12/RN01).
 *
 * <p>O histórico é append-only: lançar de novo no mesmo escopo fecha o
 * anterior. A bandeira nasce com validade padrão de 12 h e some do mapa ao
 * vencer, sem apagar o registro.
 */
@RestController
@RequestMapping("/api/v1/operations/flags")
@Tag(name = "Sinalização operacional", description = "Lançamento e encerramento de bandeiras")
@SecurityRequirement(name = "bearerAuth")
public class FlagController {

    private final FlagService flagService;

    public FlagController(FlagService flagService) {
        this.flagService = flagService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Lançar bandeira",
            description = "Encerra a bandeira vigente do mesmo escopo (praia inteira ou setor) "
                    + "e registra a nova. A validade padrão é 12 h; o pedido pode informar "
                    + "de 0,5 h a 24 h. Ponto fora do mapeamento não é recusado: o lançamento "
                    + "vira um registro de área não identificada para a coordenação triar, "
                    + "sem publicar bandeira no mapa.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Bandeira publicada ou registro enviado à triagem",
                    content = @Content(schema = @Schema(implementation = FlagLaunchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Área/setor inválido ou payload inválido (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão válida (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public FlagLaunchResponse launch(
            @Valid @RequestBody LaunchFlagRequest request,
            Authentication authentication) {
        return flagService.launch(userId(authentication), request);
    }

    @PostMapping("/{flagId}/close")
    @Operation(
            summary = "Encerrar sinalização",
            description = "Fecha a bandeira antes da validade; ela sai do mapa e continua no histórico.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sinalização encerrada",
                    content = @Content(schema = @Schema(implementation = FlagResponse.class))),
            @ApiResponse(responseCode = "404", description = "Sinalização inexistente ou já encerrada (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão válida (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public FlagResponse close(
            @Parameter(description = "Identificador do lançamento")
            @PathVariable UUID flagId,
            Authentication authentication) {
        return flagService.close(userId(authentication), flagId);
    }

    private static UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
