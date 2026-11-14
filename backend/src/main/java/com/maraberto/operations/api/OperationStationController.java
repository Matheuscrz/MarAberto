package com.maraberto.operations.api;

import com.maraberto.operations.application.StationService;
import com.maraberto.operations.domain.StationKind;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro, reposição e ativação de estruturas em campo (RF11).
 *
 * <p>O guarda-vidas cadastra o posto/base onde está, anexa fotos do posto e
 * repõe a posição quando muda de lugar, sempre com a leitura atual do aparelho.
 * Ele também desativa a estrutura do próprio plantão quando precisa sair ou
 * quando o posto fica indisponível — o pin sai do mapa sem esperar a TI. A
 * coordenação revê o resultado em {@code /admin/postos}, com a origem, a
 * precisão e as fotos.
 */
@RestController
@RequestMapping("/api/v1/operations/stations")
@Tag(name = "Estruturas (campo)", description = "Cadastro e reposição com a posição do aparelho")
@SecurityRequirement(name = "bearerAuth")
public class OperationStationController {

    private final StationService stationService;

    public OperationStationController(StationService stationService) {
        this.stationService = stationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Cadastrar posto ou base na posição atual",
            description = "Posto fixo exige a área de banho; base móvel não pertence a uma praia. "
                    + "A coordenada vem do aparelho e a origem fica registrada como DEVICE_GPS. "
                    + "As fotos já enviadas pelo operador são anexadas ao cadastro (até 3).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Estrutura cadastrada",
                    content = @Content(schema = @Schema(implementation = StationAdminView.class))),
            @ApiResponse(responseCode = "400", description = "Payload ou área inválidos (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão válida (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public StationAdminView create(
            @Valid @RequestBody CreateStationRequest request,
            Authentication authentication) {
        return stationService.createStation(userId(authentication), request);
    }

    @GetMapping("/{kind}/{stationId}")
    @Operation(
            summary = "Ler estrutura do console de campo",
            description = "Devolve a estrutura mesmo desativada, para o operador ver o "
                    + "estado e reativá-la quando voltar ao posto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estrutura",
                    content = @Content(schema = @Schema(implementation = StationAdminView.class))),
            @ApiResponse(responseCode = "404", description = "Estrutura inexistente (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public StationAdminView get(
            @Parameter(description = "Tipo da estrutura", example = "FIXED")
            @PathVariable StationKind kind,
            @PathVariable UUID stationId) {
        return stationService.station(kind, stationId);
    }

    @PatchMapping("/{kind}/{stationId}")
    @Operation(
            summary = "Ativar ou desativar estrutura do próprio plantão",
            description = "O guarda-vidas alterna o posto/base do seu plantão aberto; a "
                    + "coordenação e a TI alternam qualquer estrutura. Desativar tira o pin do "
                    + "mapa na hora sem apagar o histórico de plantões e ocorrências.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado atualizado",
                    content = @Content(schema = @Schema(implementation = StationAdminView.class))),
            @ApiResponse(responseCode = "400", description = "Payload inválido (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "403", description = "Estrutura fora do plantão do operador (RFC 9457)",
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
            @Valid @RequestBody StationStatusRequest request,
            Authentication authentication) {
        return stationService.setActiveInField(
                userId(authentication),
                isPrivileged(authentication),
                kind,
                stationId,
                Boolean.TRUE.equals(request.active()),
                request.statusNote());
    }

    @PatchMapping("/{kind}/{stationId}/position")
    @Operation(
            summary = "Reposicionar estrutura",
            description = "Atualiza a posição oficial com a leitura atual do aparelho. Plantões e "
                    + "ocorrências continuam apontando para a mesma estrutura.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Posição atualizada",
                    content = @Content(schema = @Schema(implementation = StationAdminView.class))),
            @ApiResponse(responseCode = "400", description = "Payload inválido (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "404", description = "Estrutura inexistente (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public StationAdminView move(
            @Parameter(description = "Tipo da estrutura", example = "FIXED")
            @PathVariable StationKind kind,
            @PathVariable UUID stationId,
            @Valid @RequestBody StationPositionRequest request) {
        return stationService.moveStation(kind, stationId, request);
    }

    private static UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    /** Coordenação e TI veem e alteram qualquer estrutura; o guarda-vidas, só a do plantão. */
    private static boolean isPrivileged(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> authority.equals("ROLE_COORDINATOR")
                        || authority.equals("ROLE_ADMIN"));
    }
}
