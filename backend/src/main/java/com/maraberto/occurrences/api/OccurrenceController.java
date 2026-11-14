package com.maraberto.occurrences.api;

import com.maraberto.occurrences.application.OccurrenceCommandService;
import com.maraberto.occurrences.application.OccurrenceQueryService;
import com.maraberto.occurrences.domain.OccurrenceStatus;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ocorrências do atendimento (ADR-013).
 *
 * <p>A ocorrência nasce no ack do SOS e é encerrada pelo guarda-vidas que
 * atendeu; a coordenação completa os dados depois. A coordenada do banhista
 * aparece só aqui, em rota autenticada e limitada ao escopo de quem consulta
 * (RN15/RN17). Dados de vítima exigem revelação explícita e auditada.
 */
@RestController
@RequestMapping("/api/v1/occurrences")
@Tag(name = "Ocorrências", description = "Ficha operacional do atendimento e pendência de CIOSP")
@SecurityRequirement(name = "bearerAuth")
public class OccurrenceController {

    private final OccurrenceQueryService occurrenceQueryService;
    private final OccurrenceCommandService occurrenceCommandService;

    public OccurrenceController(
            OccurrenceQueryService occurrenceQueryService,
            OccurrenceCommandService occurrenceCommandService) {
        this.occurrenceQueryService = occurrenceQueryService;
        this.occurrenceCommandService = occurrenceCommandService;
    }

    @GetMapping
    @Operation(
            summary = "Listar ocorrências",
            description = "Guarda-vidas vê as ocorrências que ele mesmo atendeu; coordenação e TI "
                    + "veem todas. Ordenadas da mais recente para a mais antiga, pelo horário do "
                    + "fato. O filtro `status` separa o que está em atendimento do histórico.")
    @ApiResponse(responseCode = "200", description = "Ocorrências",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = OccurrenceView.class))))
    public List<OccurrenceView> list(
            @Parameter(description = "Máximo de ocorrências devolvidas (1 a 100)",
                    example = "50")
            @RequestParam(required = false, defaultValue = "50") int limit,
            @Parameter(description = "Filtra por situação; omitido devolve todas")
            @RequestParam(required = false) OccurrenceStatus status,
            Authentication authentication) {
        return occurrenceQueryService.list(
                userId(authentication), seesAllScopes(authentication), status, limit);
    }

    @GetMapping("/{occurrenceId}")
    @Operation(summary = "Ficha da ocorrência",
            description = "Ocorrência fora do escopo de quem consulta responde 404 — não existe "
                    + "distinção entre \"não existe\" e \"não é sua\". O resumo do atendimento é "
                    + "decifrado; os dados de vítima continuam mascarados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ocorrência",
                    content = @Content(schema = @Schema(
                            implementation = OccurrenceDetailView.class))),
            @ApiResponse(responseCode = "404", description = "Ocorrência não encontrada (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public OccurrenceDetailView get(
            @PathVariable UUID occurrenceId,
            Authentication authentication) {
        return occurrenceQueryService.get(
                occurrenceId, userId(authentication), seesAllScopes(authentication));
    }

    @PostMapping("/{occurrenceId}/close")
    @Operation(summary = "Encerrar o atendimento",
            description = "Registra o relato, o desfecho e os dados opcionais da vítima. A "
                    + "ocorrência sai da lista em atendimento e o chamado do SOS vinculado é "
                    + "encerrado na mesma transação. Ocorrência já encerrada responde 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ocorrência encerrada",
                    content = @Content(schema = @Schema(
                            implementation = OccurrenceDetailView.class))),
            @ApiResponse(responseCode = "404", description = "Ocorrência fora do escopo (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Ocorrência já encerrada (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public OccurrenceDetailView close(
            @PathVariable UUID occurrenceId,
            @Valid @RequestBody CloseOccurrenceRequest request,
            Authentication authentication) {
        return occurrenceCommandService.close(
                occurrenceId, userId(authentication), seesAllScopes(authentication), request);
    }

    @PatchMapping("/{occurrenceId}")
    @Operation(summary = "Editar a ficha",
            description = "Corrige ou completa dados, inclusive depois do encerramento (vítima "
                    + "identificada depois, protocolo do CIOSP, resumo). Campo nulo não altera o "
                    + "valor atual.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ocorrência atualizada",
                    content = @Content(schema = @Schema(
                            implementation = OccurrenceDetailView.class))),
            @ApiResponse(responseCode = "404", description = "Ocorrência fora do escopo (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Pedido inconsistente com a ficha "
                    + "(RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public OccurrenceDetailView update(
            @PathVariable UUID occurrenceId,
            @Valid @RequestBody UpdateOccurrenceRequest request,
            Authentication authentication) {
        return occurrenceCommandService.update(
                occurrenceId, userId(authentication), seesAllScopes(authentication), request);
    }

    @PostMapping("/{occurrenceId}/victim/reveal")
    @Operation(summary = "Revelar dados de vítima",
            description = "Decifra nome, idade, contato e condição da vítima. O motivo é "
                    + "obrigatório e a revelação entra no histórico da ocorrência (RN15).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dados de vítima",
                    content = @Content(schema = @Schema(implementation = VictimView.class))),
            @ApiResponse(responseCode = "404", description = "Sem dados de vítima ou fora do "
                    + "escopo (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public VictimView revealVictim(
            @PathVariable UUID occurrenceId,
            @Valid @RequestBody RevealVictimRequest request,
            Authentication authentication) {
        return occurrenceCommandService.reveal(
                occurrenceId, userId(authentication), seesAllScopes(authentication), request);
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
