package com.maraberto.operations.api;

import com.maraberto.operations.application.ShiftService;
import com.maraberto.operations.domain.ShiftNotFoundException;
import com.maraberto.operations.domain.ShiftSession;
import com.maraberto.shared.api.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plantão do operador. Abrir o plantão é o passo que o torna elegível para
 * receber o SOS (ADR-018) e alimenta o painel da coordenação.
 */
@RestController
@RequestMapping("/api/v1/dispatch/shifts")
@Tag(name = "Plantões", description = "Abertura, posição e encerramento do plantão do operador")
@SecurityRequirement(name = "bearerAuth")
public class ShiftController {

    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abrir plantão",
            description = "Vincula o operador autenticado a um posto fixo ou a uma base móvel.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Plantão aberto",
                    content = @Content(schema = @Schema(implementation = ShiftResponse.class))),
            @ApiResponse(responseCode = "400", description = "Destino inválido (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Operador já tem plantão aberto (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ShiftResponse open(
            @Valid @RequestBody OpenShiftRequest request,
            Authentication authentication) {
        return ShiftResponse.from(shiftService.open(userId(authentication),
                new ShiftService.OpenShiftCommand(
                        request.postId(),
                        request.mobileBaseId(),
                        request.latitude(),
                        request.longitude(),
                        request.accuracyMeters())));
    }

    @GetMapping("/targets")
    @Operation(summary = "Postos e bases para plantão",
            description = "Estruturas ativas onde o operador pode abrir plantão, "
                    + "com a posição usada na seleção espacial do SOS.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estruturas ativas",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = ShiftTargetResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Sem sessão válida (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public List<ShiftTargetResponse> targets() {
        return shiftService.targets().stream().map(ShiftTargetResponse::from).toList();
    }

    @GetMapping("/current")
    @Operation(summary = "Plantão atual", description = "Plantão aberto do operador autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plantão aberto",
                    content = @Content(schema = @Schema(implementation = ShiftResponse.class))),
            @ApiResponse(responseCode = "404", description = "Sem plantão aberto (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ShiftResponse current(Authentication authentication) {
        return shiftService.current(userId(authentication))
                .map(ShiftResponse::from)
                .orElseThrow(ShiftNotFoundException::new);
    }

    @PutMapping("/current/position")
    @Operation(summary = "Atualizar posição do plantão",
            description = "Registra a última posição conhecida; usada apenas para elegibilidade e painel autorizado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Posição atualizada",
                    content = @Content(schema = @Schema(implementation = ShiftResponse.class))),
            @ApiResponse(responseCode = "404", description = "Sem plantão aberto (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ShiftResponse move(
            @Valid @RequestBody UpdatePositionRequest request,
            Authentication authentication) {
        return ShiftResponse.from(shiftService.move(userId(authentication),
                request.latitude(), request.longitude(), request.accuracyMeters()));
    }

    @PostMapping("/current/close")
    @Operation(summary = "Encerrar plantão",
            description = "Fecha o plantão aberto do operador; ele deixa de ser elegível para o SOS.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plantão encerrado",
                    content = @Content(schema = @Schema(implementation = ShiftResponse.class))),
            @ApiResponse(responseCode = "404", description = "Sem plantão aberto (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ShiftResponse close(Authentication authentication) {
        return ShiftResponse.from(shiftService.close(userId(authentication)));
    }

    private static UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    @Schema(name = "OpenShiftRequest", description = "Destino e posição inicial do plantão")
    public record OpenShiftRequest(
            @Schema(description = "Posto fixo do plantão (mutuamente exclusivo com a base móvel)")
            UUID postId,
            @Schema(description = "Base móvel do plantão (mutuamente exclusivo com o posto fixo)")
            UUID mobileBaseId,
            @Schema(description = "Latitude atual do aparelho, quando aplicável", example = "-10.9472")
            @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @Schema(description = "Longitude atual do aparelho, quando aplicável", example = "-37.0731")
            @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @Schema(description = "Precisão estimada da posição, em metros", example = "12.5")
            @Positive @DecimalMax("100000.0") Double accuracyMeters) {
    }

    @Schema(name = "UpdateShiftPositionRequest", description = "Nova posição conhecida do plantão")
    public record UpdatePositionRequest(
            @Schema(description = "Latitude WGS84", example = "-10.9472", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @Schema(description = "Longitude WGS84", example = "-37.0731", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @Schema(description = "Precisão estimada da posição, em metros", example = "12.5")
            @Positive @DecimalMax("100000.0") Double accuracyMeters) {
    }

    @Schema(name = "ShiftTargetResponse", description = "Posto fixo ou base móvel disponível para plantão")
    public record ShiftTargetResponse(
            UUID id,
            @Schema(description = "Tipo da estrutura", example = "POST",
                    allowableValues = {"POST", "MOBILE_BASE"})
            ShiftTargetKind kind,
            @Schema(description = "Nome do posto ou da base", example = "Posto Pajuçara 01")
            String name,
            @Schema(description = "Praia do posto fixo; nula para base móvel", example = "Praia de Pajuçara")
            String beachName,
            @Schema(description = "Latitude WGS84 da estrutura", example = "-9.6658")
            double latitude,
            @Schema(description = "Longitude WGS84 da estrutura", example = "-35.7089")
            double longitude,
            @Schema(description = "Raio de cobertura cadastrado, em metros; nulo usa o padrão",
                    example = "2000")
            Integer coverageRadiusMeters) {

        static ShiftTargetResponse from(ShiftTarget target) {
            return new ShiftTargetResponse(
                    target.id(),
                    target.kind(),
                    target.name(),
                    target.beachName(),
                    target.latitude(),
                    target.longitude(),
                    target.coverageRadiusMeters());
        }
    }

    @Schema(name = "ShiftResponse", description = "Plantão do operador")
    public record ShiftResponse(
            UUID id,
            UUID postId,
            UUID mobileBaseId,
            Instant startedAt,
            Instant endedAt,
            Instant lastPositionAt,
            Double lastPositionAccuracyMeters) {

        static ShiftResponse from(ShiftSession shift) {
            return new ShiftResponse(
                    shift.getId(),
                    shift.getPostId(),
                    shift.getMobileBaseId(),
                    shift.getStartedAt(),
                    shift.getEndedAt(),
                    shift.getLastPositionAt(),
                    shift.getLastPositionAccuracyMeters());
        }
    }
}
