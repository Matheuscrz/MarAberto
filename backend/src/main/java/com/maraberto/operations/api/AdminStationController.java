package com.maraberto.operations.api;

import com.maraberto.operations.application.StationService;
import com.maraberto.operations.domain.StationKind;
import com.maraberto.shared.api.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro e estado das estruturas operacionais no painel da TI (`/admin`).
 *
 * <p>A TI vê e ativa/desativa postos e bases; o mapa público só recebe as
 * ativas. Desativar não apaga plantões nem ocorrências do histórico.
 */
@RestController
@RequestMapping("/api/v1/admin/stations")
@Tag(name = "Estruturas (TI)", description = "Estado de ativação de postos e bases móveis")
@SecurityRequirement(name = "bearerAuth")
public class AdminStationController {

    private final StationService stationService;

    public AdminStationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    @Operation(summary = "Listar estruturas",
            description = "Todos os postos e bases, ativos e inativos, com motivo e data da desativação.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estruturas cadastradas",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = StationAdminView.class)))),
            @ApiResponse(responseCode = "401", description = "Sem sessão válida (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "403", description = "Sem papel de TI (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public List<StationAdminView> list() {
        return stationService.adminStations();
    }

    @PatchMapping("/{kind}/{stationId}")
    @Operation(summary = "Ativar ou desativar estrutura",
            description = "Estrutura desativada some do mapa público e da lista de destinos do "
                    + "plantão; o histórico de plantões e ocorrências permanece.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado atualizado",
                    content = @Content(schema = @Schema(implementation = StationAdminView.class))),
            @ApiResponse(responseCode = "400", description = "Payload inválido (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "404", description = "Estrutura inexistente (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public StationAdminView updateStatus(
            @Parameter(description = "Tipo da estrutura", example = "FIXED")
            @PathVariable StationKind kind,
            @PathVariable UUID stationId,
            @Valid @RequestBody StationStatusRequest request) {
        return stationService.setActive(
                kind, stationId, Boolean.TRUE.equals(request.active()), request.statusNote());
    }
}
