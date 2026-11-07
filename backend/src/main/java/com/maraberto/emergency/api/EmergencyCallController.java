package com.maraberto.emergency.api;

import com.maraberto.emergency.application.CreateEmergencyCallService;
import com.maraberto.emergency.domain.EmergencyCall;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/emergency-calls")
@Tag(name = "Chamadas de Emergência", description = "API para chamadas de emergência")
public class EmergencyCallController {

    private final CreateEmergencyCallService createEmergencyCallService;

    public EmergencyCallController(CreateEmergencyCallService createEmergencyCallService) {
        this.createEmergencyCallService = createEmergencyCallService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Criar chamada de emergência",
            description = "Registra o SOS de forma idempotente antes da notificação operacional.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Chamada de emergência criada com sucesso",
                    content = @Content(schema = @Schema(implementation = EmergencyCallResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requisição inválida; resposta no formato RFC 9457",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erro inesperado; resposta no formato RFC 9457",
                    content = @Content(mediaType = "application/problem+json"))
    })
    public EmergencyCallResponse create(@Valid @RequestBody CreateEmergencyCallRequest request) {
        EmergencyCall call = createEmergencyCallService
                .create(new CreateEmergencyCallService.CreateEmergencyCallCommand(
                        request.clientRequestId(),
                        request.latitude(),
                        request.longitude(),
                        request.accuracyMeters()));
        return EmergencyCallResponse.from(call);
    }

    public record CreateEmergencyCallRequest(
            @NotBlank String clientRequestId,
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @Positive Double accuracyMeters) {
    }

    public record EmergencyCallResponse(
            UUID callId,
            String status,
            String clientRequestId,
            Instant createdAt) {

        private static EmergencyCallResponse from(EmergencyCall call) {
            return new EmergencyCallResponse(
                    call.getId(),
                    call.getStatus().name(),
                    call.getClientRequestId(),
                    call.getCreatedAt());
        }
    }
}
