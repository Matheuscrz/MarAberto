package com.maraberto.content.api;

import com.maraberto.content.application.ContentService;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD do CMS educativo e de divulgação pela Coordenação (`COORDINATOR`/`ADMIN`).
 */
@RestController
@RequestMapping("/api/v1/management/content")
@Tag(name = "CMS Aprender (Gestão)", description = "Cadastro, edição, ordenação e publicação de conteúdos")
@SecurityRequirement(name = "bearerAuth")
public class ManagementContentController {

    private final ContentService service;

    public ManagementContentController(ContentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar todos os conteúdos (publicados e rascunhos)")
    @ApiResponse(responseCode = "200", description = "Catálogo completo",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = ContentView.class))))
    public List<ContentView> list(
            @Parameter(description = "Filtrar por tipo (VIDEO, GUIDE, CAMPAIGN)")
            @RequestParam(required = false) ContentKind kind,
            @Parameter(description = "Filtrar por estado de publicação")
            @RequestParam(required = false) Boolean published) {
        return service.listForManagement(kind, published);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar conteúdo educativo ou material de divulgação")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conteúdo criado",
                    content = @Content(schema = @Schema(implementation = ContentView.class))),
            @ApiResponse(responseCode = "400", description = "Dados ou URL/ID do YouTube inválidos (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ContentView create(
            @Valid @RequestBody CreateContentRequest request,
            Authentication authentication) {
        return service.create(UUID.fromString(authentication.getName()), request);
    }

    @PutMapping("/{contentId}")
    @Operation(summary = "Atualizar conteúdo existente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conteúdo atualizado",
                    content = @Content(schema = @Schema(implementation = ContentView.class))),
            @ApiResponse(responseCode = "400", description = "Dados ou URL/ID do YouTube inválidos (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "404", description = "Conteúdo não encontrado (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ContentView update(
            @PathVariable UUID contentId,
            @Valid @RequestBody UpdateContentRequest request,
            Authentication authentication) {
        return service.update(UUID.fromString(authentication.getName()), contentId, request);
    }

    @PatchMapping("/{contentId}/published")
    @Operation(summary = "Publicar ou despublicar conteúdo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado atualizado",
                    content = @Content(schema = @Schema(implementation = ContentView.class))),
            @ApiResponse(responseCode = "404", description = "Conteúdo não encontrado (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ContentView setPublished(
            @PathVariable UUID contentId,
            @Valid @RequestBody PublishContentRequest request,
            Authentication authentication) {
        return service.setPublished(
                UUID.fromString(authentication.getName()), contentId, request.published());
    }

    @PutMapping("/reorder")
    @Operation(summary = "Reordenar conteúdos em lote")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista reordenada",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = ContentView.class)))),
            @ApiResponse(responseCode = "400", description = "Lista inválida ou com IDs duplicados (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public List<ContentView> reorder(
            @Valid @RequestBody ReorderContentRequest request,
            Authentication authentication) {
        return service.reorder(UUID.fromString(authentication.getName()), request.orderedIds());
    }

    @DeleteMapping("/{contentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir conteúdo")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Conteúdo excluído"),
            @ApiResponse(responseCode = "404", description = "Conteúdo não encontrado (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public void delete(
            @PathVariable UUID contentId,
            Authentication authentication) {
        service.delete(UUID.fromString(authentication.getName()), contentId);
    }
}
