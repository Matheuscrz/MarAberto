package com.maraberto.emergency.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Contrato documentado para respostas de erro no formato RFC 9457.
 *
 * <p>A aplicação usa {@code ProblemDetail} para serializar a resposta; este
 * record mantém o mesmo contrato explícito no OpenAPI.
 */
@Schema(name = "ApiProblem", description = "Problema HTTP no formato RFC 9457")
public record ApiProblem(
        @Schema(description = "URI estável que identifica o tipo do problema",
                example = "https://maraberto.org/problems/invalid-request")
        String type,
        @Schema(description = "Código HTTP retornado", example = "400")
        int status,
        @Schema(description = "Título curto e legível do problema", example = "Requisição inválida")
        String title,
        @Schema(description = "Detalhamento seguro do problema", example = "Um ou mais campos não atendem às regras de validação.")
        String detail,
        @Schema(description = "URI da requisição que originou o problema", example = "/api/v1/emergency-calls")
        String instance,
        @Schema(description = "Erros por campo, quando a requisição falha na validação")
        List<FieldValidationError> errors) {
}
