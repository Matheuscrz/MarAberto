package com.maraberto.emergency.api;

import com.maraberto.emergency.application.DispatchService;
import com.maraberto.shared.api.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Console operacional do SOS: reconciliação dos chamados abertos e ack.
 *
 * <p>As coordenadas só aparecem aqui, em rota autenticada e restrita ao
 * operador que recebeu o despacho. Nenhum dado do chamado vai para tópico
 * aberto, log ou métrica.
 */
@RestController
@RequestMapping("/api/v1/dispatch/calls")
@Tag(name = "Despacho do SOS", description = "Chamados atribuídos ao operador e assunção do atendimento")
@SecurityRequirement(name = "bearerAuth")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @GetMapping
    @Operation(summary = "Chamados abertos do operador",
            description = "Reconciliação por HTTP: devolve os SOS atribuídos ao operador que ainda não foram assumidos.")
    @ApiResponse(responseCode = "200", description = "Lista de chamados abertos",
            content = @Content(schema = @Schema(implementation = OpenCallResponse.class)))
    public List<OpenCallResponse> openCalls(Authentication authentication) {
        return dispatchService.openCalls(userId(authentication)).stream()
                .map(OpenCallResponse::from)
                .toList();
    }

    @GetMapping("/assigned")
    @Operation(summary = "Chamados em atendimento do operador",
            description = "SOS que o operador já assumiu e ainda estão em atendimento. É a rota "
                    + "autenticada que mantém a localização do banhista disponível depois do ack "
                    + "(rota no mapa) e a referência para a ocorrência operacional.")
    @ApiResponse(responseCode = "200", description = "Lista de chamados em atendimento",
            content = @Content(schema = @Schema(implementation = AssignedCallResponse.class)))
    public List<AssignedCallResponse> assignedCalls(Authentication authentication) {
        return dispatchService.assignedCalls(userId(authentication)).stream()
                .map(AssignedCallResponse::from)
                .toList();
    }

    @PostMapping("/{callId}/ack")
    @Operation(summary = "Assumir o chamado",
            description = "Compare-and-set: o primeiro operador que assumir vence; os demais recebem 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chamado assumido",
                    content = @Content(schema = @Schema(implementation = AckResponse.class))),
            @ApiResponse(responseCode = "403", description = "Chamado não atribuído ao operador (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Chamado já assumido por outro operador (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public AckResponse acknowledge(
            @PathVariable UUID callId,
            Authentication authentication) {
        return AckResponse.from(dispatchService.acknowledge(callId, userId(authentication)));
    }

    private static UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    @Schema(name = "OpenCallResponse", description = "SOS aberto atribuído ao operador")
    public record OpenCallResponse(
            UUID callId,
            String status,
            double latitude,
            double longitude,
            Double accuracyMeters,
            Instant createdAt,
            UUID dispatchId,
            int round,
            BigDecimal distanceMeters,
            boolean positionStale) {

        static OpenCallResponse from(DispatchService.OpenCall openCall) {
            return new OpenCallResponse(
                    openCall.callId(),
                    openCall.status(),
                    openCall.latitude(),
                    openCall.longitude(),
                    openCall.accuracyMeters(),
                    openCall.createdAt(),
                    openCall.dispatchId(),
                    openCall.round(),
                    openCall.distanceMeters(),
                    openCall.positionStale());
        }
    }

    @Schema(name = "AssignedCallResponse", description = "SOS assumido pelo operador, ainda em atendimento")
    public record AssignedCallResponse(
            UUID callId,
            String status,
            double latitude,
            double longitude,
            Double accuracyMeters,
            Instant createdAt,
            Instant acknowledgedAt,
            UUID dispatchId,
            int round,
            BigDecimal distanceMeters,
            boolean positionStale,
            BigDecimal currentDistanceMeters,
            Instant currentPositionAt,
            boolean currentPositionStale) {

        static AssignedCallResponse from(DispatchService.AssignedCall call) {
            return new AssignedCallResponse(
                    call.callId(),
                    call.status(),
                    call.latitude(),
                    call.longitude(),
                    call.accuracyMeters(),
                    call.createdAt(),
                    call.acknowledgedAt(),
                    call.dispatchId(),
                    call.round(),
                    call.distanceMeters(),
                    call.positionStale(),
                    call.currentDistanceMeters(),
                    call.currentPositionAt(),
                    call.currentPositionStale());
        }
    }

    @Schema(name = "AckResponse", description = "Resultado da assunção do chamado")
    public record AckResponse(
            UUID callId,
            String status,
            UUID dispatchId,
            UUID assignedPostId,
            Instant acknowledgedAt) {

        static AckResponse from(DispatchService.AckResult result) {
            return new AckResponse(
                    result.callId(),
                    result.status(),
                    result.dispatchId(),
                    result.assignedPostId(),
                    result.acknowledgedAt());
        }
    }
}
