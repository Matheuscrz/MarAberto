package com.maraberto.shared.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Erro de validação associado a um campo, usado no contrato RFC 9457 da API.
 */
@Schema(name = "FieldValidationError", description = "Erro de validação associado a um campo")
public record FieldValidationError(
        @Schema(description = "Nome do campo inválido", example = "latitude")
        String field,
        @Schema(description = "Motivo da falha de validação", example = "deve ser menor ou igual a 90")
        String message) {
}
