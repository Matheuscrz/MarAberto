package com.maraberto.audit.api;

import com.maraberto.audit.application.AuditIntegrityService;
import com.maraberto.audit.application.AuditQueryService;
import com.maraberto.shared.api.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta da trilha de auditoria pela TI (RNF06, ADR-027).
 *
 * <p>Só {@code ADMIN} chega aqui (regra em {@code SecurityConfig}). A resposta
 * é {@code no-store}: a trilha pode revelar rotinas e horários da operação.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Auditoria (TI)", description = "Trilha imutável de ações sensíveis")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditQueryService queryService;
    private final AuditIntegrityService integrityService;

    public AuditController(
            AuditQueryService queryService,
            AuditIntegrityService integrityService) {
        this.queryService = queryService;
        this.integrityService = integrityService;
    }

    @GetMapping
    @Operation(summary = "Consultar a trilha",
            description = "Página da mais recente para a mais antiga. Filtre por ator, ação, "
                    + "entidade, traceId e período; devolva `nextCursor` em `cursor` para "
                    + "a página seguinte.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página da trilha",
                    content = @Content(schema = @Schema(implementation = AuditPage.class))),
            @ApiResponse(responseCode = "400", description = "Cursor ou filtro inválido (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão válida (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "403", description = "Sem papel de TI (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<AuditPage> list(
            @Parameter(description = "Operador que executou o ato")
            @RequestParam(required = false) UUID actorId,
            @Parameter(description = "Ação registrada", example = "USER_DEACTIVATED")
            @RequestParam(required = false) String action,
            @Parameter(description = "Tipo da entidade", example = "USER")
            @RequestParam(required = false) String entityType,
            @Parameter(description = "Identificador da entidade")
            @RequestParam(required = false) String entityId,
            @Parameter(description = "Identificador da requisição")
            @RequestParam(required = false) String traceId,
            @Parameter(description = "Início do período (ISO-8601, inclusive)")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "Fim do período (ISO-8601, exclusivo)")
            @RequestParam(required = false) Instant to,
            @Parameter(description = "Cursor opaco devolvido em nextCursor")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Tamanho da página (1 a 100)", example = "50")
            @RequestParam(required = false, defaultValue = "50") int limit) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(queryService.list(
                        actorId, action, entityType, entityId, traceId,
                        from, to, cursor, limit));
    }

    @GetMapping("/integrity")
    @Operation(summary = "Conferir a cadeia de hashes",
            description = "Recalcula toda a trilha e devolve a primeira linha divergente, se houver.")
    @ApiResponse(responseCode = "200", description = "Resultado da conferência",
            content = @Content(schema = @Schema(implementation = AuditIntegrityView.class)))
    public ResponseEntity<AuditIntegrityView> integrity() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(integrityService.verify());
    }
}
