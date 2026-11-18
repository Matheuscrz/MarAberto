package com.maraberto.operations.api;

import com.maraberto.operations.application.ManagementTeamService;
import com.maraberto.shared.api.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Equipe em plantão para o mapa de situação da coordenação (RF40/ADR-026).
 *
 * <p>A rota é restrita a {@code COORDINATOR}/{@code ADMIN} pela configuração de
 * segurança e responde {@code Cache-Control: no-store}: a posição do operador é
 * dado sensível (RN17) e não pode ser guardada por cache, proxy ou histórico do
 * navegador.
 */
@RestController
@RequestMapping("/api/v1/management/team")
@Tag(name = "Coordenação (equipe)",
        description = "Plantões abertos, última posição e volume do dia")
@SecurityRequirement(name = "bearerAuth")
public class ManagementTeamController {

    private final ManagementTeamService teamService;

    public ManagementTeamController(ManagementTeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    @Operation(
            summary = "Equipe em plantão",
            description = "Devolve cada plantão aberto com lease vivo, a última posição conhecida "
                    + "e os contadores do dia. Posição mais velha que `position-stale-after` vem "
                    + "marcada como `stale`. A resposta nunca é cacheada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipe em plantão",
                    content = @Content(schema = @Schema(implementation = ManagementTeamView.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão válida (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "403", description = "Fora da coordenação (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<ManagementTeamView> team() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(teamService.team());
    }
}
