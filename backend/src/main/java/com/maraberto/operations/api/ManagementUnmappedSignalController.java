package com.maraberto.operations.api;

import com.maraberto.operations.application.UnmappedSignalService;
import com.maraberto.operations.domain.UnmappedSignalStatus;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Triagem da sinalização registrada fora do mapeamento (RF12/RN17).
 *
 * <p>O guarda-vidas registra o ponto; a coordenação decide o que ele é. Listar
 * e decidir são rotas de `/api/v1/management/**` (coordenação, com a TI como
 * superusuário técnico) e nada aqui é publicado no mapa público.
 */
@RestController
@RequestMapping("/api/v1/management/unmapped-signals")
@Tag(name = "Áreas não identificadas",
        description = "Fila de sinalizações registradas fora do mapeamento")
@SecurityRequirement(name = "bearerAuth")
public class ManagementUnmappedSignalController {

    private final UnmappedSignalService service;

    public ManagementUnmappedSignalController(UnmappedSignalService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Listar registros de área não identificada",
            description = "Sem filtro devolve a fila inteira, das mais recentes para as mais "
                    + "antigas. Cada registro traz a área ativa mais próxima como sugestão de "
                    + "triagem; o vínculo só é gravado quando a coordenação decide.")
    @ApiResponse(responseCode = "200", description = "Registros da fila",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = UnmappedSignalView.class))))
    public List<UnmappedSignalView> list(
            @Parameter(description = "Estado da triagem; omitido devolve todos",
                    example = "PENDING")
            @RequestParam(required = false) UnmappedSignalStatus status,
            @Parameter(description = "Máximo de registros devolvidos (1 a 200)", example = "100")
            @RequestParam(required = false, defaultValue = "100") int limit) {
        return service.list(status, limit);
    }

    @PostMapping("/{signalId}/decision")
    @Operation(
            summary = "Decidir o destino do registro",
            description = "IDENTIFIED associa uma área existente (e, opcionalmente, um setor "
                    + "dela); NEEDS_MAPPING pede um novo mapeamento; DISCARDED encerra sem "
                    + "ação; PENDING reabre a fila. Decidir não publica bandeira no mapa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Decisão registrada",
                    content = @Content(schema = @Schema(implementation = UnmappedSignalView.class))),
            @ApiResponse(responseCode = "400", description = "Decisão inválida ou área/setor inconsistente (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "404", description = "Registro não encontrado (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public UnmappedSignalView decide(
            @PathVariable UUID signalId,
            @Valid @RequestBody UnmappedSignalDecisionRequest request,
            Authentication authentication) {
        return service.decide(
                UUID.fromString(authentication.getName()), signalId, request);
    }
}
