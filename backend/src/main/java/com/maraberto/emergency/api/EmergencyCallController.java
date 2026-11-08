package com.maraberto.emergency.api;

import com.maraberto.emergency.application.CreateEmergencyCallService;
import com.maraberto.emergency.domain.EmergencyCall;
import com.maraberto.shared.security.sanitization.SanitizationPolicy;
import com.maraberto.shared.security.sanitization.Sanitized;
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
import jakarta.validation.constraints.Pattern;
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
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erro inesperado; resposta no formato RFC 9457",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
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

    @Schema(name = "CreateEmergencyCallRequest", description = "Dados mínimos para registrar um SOS")
    public record CreateEmergencyCallRequest(
            @Schema(description = "Identificador idempotente gerado pelo cliente (UUID)", example = "8d6a5a87-1c43-4af4-9b7f-9f2d5a5a4f5c",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "deve ser um UUID")
            @Sanitized(value = SanitizationPolicy.IDENTIFIER, maxLength = 64)
            String clientRequestId,
            @Schema(description = "Latitude WGS84 do pedido de socorro", example = "-10.9472",
                    minimum = "-90", maximum = "90", requiredMode = Schema.RequiredMode.REQUIRED)
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @Schema(description = "Longitude WGS84 do pedido de socorro", example = "-37.0731",
                    minimum = "-180", maximum = "180", requiredMode = Schema.RequiredMode.REQUIRED)
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @Schema(description = "Precisão estimada da localização, em metros", example = "12.5", minimum = "0")
            @Positive @DecimalMax(value = "100000.0", message = "deve ser menor ou igual a 100000")
            Double accuracyMeters) {
    }

    @Schema(name = "EmergencyCallResponse", description = "Resultado do registro do SOS")
    public record EmergencyCallResponse(
            @Schema(description = "Identificador da chamada de emergência")
            UUID callId,
            @Schema(description = "Estado atual do SOS", example = "DISPATCHED")
            String status,
            @Schema(description = "Identificador idempotente recebido do cliente")
            String clientRequestId,
            @Schema(description = "Data e hora de criação em UTC")
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
