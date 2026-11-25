package com.maraberto.content.api;

import com.maraberto.content.application.ContentService;
import com.maraberto.content.application.PublicContentCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Leitura pública da Central Aprender ({@code /aprender}).
 */
@RestController
@RequestMapping("/api/v1/content")
@Tag(name = "Central Aprender", description = "Vídeos educativos, guias e materiais de divulgação publicados")
public class PublicContentController {

    private static final CacheControl CACHE = CacheControl.noCache().cachePublic();

    private final ContentService service;
    private final PublicContentCache cache;

    public PublicContentController(ContentService service, PublicContentCache cache) {
        this.service = service;
        this.cache = cache;
    }

    @GetMapping
    @Operation(
            summary = "Listar conteúdos publicados",
            description = "Devolve vídeos, guias e materiais de divulgação publicados, ordenados por "
                    + "displayOrder e data de criação. O cache é invalidado imediatamente quando a "
                    + "coordenação publica, edita, reordena ou remove um item.")
    @ApiResponse(responseCode = "200", description = "Lista de conteúdos publicados",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = ContentView.class))))
    public ResponseEntity<List<ContentView>> list(
            @Parameter(description = "Filtrar por tipo (VIDEO, GUIDE, CAMPAIGN)")
            @RequestParam(required = false) ContentKind kind) {
        String key = "public:" + (kind == null ? "ALL" : kind.name());
        List<ContentView> items = cache.get(key, () -> service.listPublic(kind));
        return ResponseEntity.ok()
                .cacheControl(CACHE)
                .body(items);
    }
}
