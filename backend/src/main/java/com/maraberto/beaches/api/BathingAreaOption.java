package com.maraberto.beaches.api;

import com.maraberto.beaches.domain.BeachKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Área de banho ativa na visão da coordenação.
 *
 * <p>É a lista de escolha da triagem de áreas não identificadas e de qualquer
 * formulário que precise apontar uma área existente. Não carrega geometria: a
 * geometria do setor escolhido vem de {@code GET /api/v1/beaches/{id}/map}.
 */
@Schema(name = "BathingAreaOption", description = "Área de banho ativa para seleção na gestão")
public record BathingAreaOption(
        @Schema(description = "Identificador estável da área") UUID id,
        @Schema(description = "Nome da praia, rio ou barragem", example = "Praia da Orla") String name,
        @Schema(description = "Tipo da área de banho", example = "BEACH") BeachKind kind,
        @Schema(description = "Município da área", example = "Aracaju") String municipality,
        @Schema(description = "UF da área", example = "SE") String stateCode,
        @Schema(description = "Quantidade de setores ativos", example = "2") int sectorCount) {
}
