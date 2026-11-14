package com.maraberto.occurrences.api;

import com.maraberto.occurrences.application.OccurrenceQueryService;
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
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ocorrências do atendimento (ADR-013).
 *
 * <p>A ocorrência nasce no ack do SOS; esta rota é a leitura do console. A
 * coordenada do banhista aparece só aqui, em rota autenticada e limitada ao
 * escopo de quem consulta (RN15/RN17).
 */
@RestController
@RequestMapping("/api/v1/occurrences")
@Tag(name = "Ocorrências", description = "Ficha operacional do atendimento e pendência de CIOSP")
@SecurityRequirement(name = "bearerAuth")
public class OccurrenceController {

    private final OccurrenceQueryService occurrenceQueryService;

    public OccurrenceController(OccurrenceQueryService occurrenceQueryService) {
        this.occurrenceQueryService = occurrenceQueryService;
    }

    @GetMapping
    @Operation(
            summary = "Listar ocorrências",
            description = "Guarda-vidas vê as ocorrências do seu posto/praia e as que ele mesmo "
                    + "assumiu; coordenação e TI veem todas. Ordenadas da mais recente para a mais "
                    + "antiga, pelo horário do fato.")
    @ApiResponse(responseCode = "200", description = "Ocorrências",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = OccurrenceView.class))))
    public List<OccurrenceView> list(
            @Parameter(description = "Máximo de ocorrências devolvidas (1 a 100)",
                    example = "50")
            @RequestParam(required = false, defaultValue = "50") int limit,
            Authentication authentication) {
        return occurrenceQueryService.list(
                userId(authentication), seesAllScopes(authentication), limit);
    }

    @GetMapping("/{occurrenceId}")
    @Operation(summary = "Ficha da ocorrência",
            description = "Ocorrência fora do escopo de quem consulta responde 404 — não existe "
                    + "distinção entre \"não existe\" e \"não é sua\".")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ocorrência",
                    content = @Content(schema = @Schema(implementation = OccurrenceView.class))),
            @ApiResponse(responseCode = "404", description = "Ocorrência não encontrada (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public OccurrenceView get(
            @PathVariable UUID occurrenceId,
            Authentication authentication) {
        return occurrenceQueryService.get(
                occurrenceId, userId(authentication), seesAllScopes(authentication));
    }

    private static UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    private static boolean seesAllScopes(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_COORDINATOR".equals(authority)
                        || "ROLE_ADMIN".equals(authority));
    }
}
