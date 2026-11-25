package com.maraberto.operations.api;

import com.maraberto.operations.application.FlagService;
import com.maraberto.operations.application.PublicMapCache;
import com.maraberto.shared.api.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sinalização no mapa público (RF12/RF38).
 *
 * <p>Sem filtro, a resposta traz só o que está vigente: aberto e dentro da
 * validade. A bandeira vencida continua no histórico, mas não é exibida. Com
 * {@code beachId} e {@code includeExpired}, a ficha da praia recebe o último
 * lançamento de cada escopo para mostrar a cor com o selo de desatualizada.
 */
@RestController
@RequestMapping("/api/v1/flags")
@Tag(name = "Sinalização", description = "Bandeiras por área de banho e setor")
public class PublicFlagController {

    /**
     * O navegador pode guardar a resposta, mas precisa revalidar a cada uso.
     *
     * <p>Não há como empurrar a invalidação de um cache HTTP sem abrir um canal
     * com o banhista (ADR-022). Então o mapa consulta o servidor no ciclo de
     * 30 s e recebe o estado atual; o cache de memória do servidor absorve a
     * repetição entre visitantes.
     */
    private static final CacheControl CACHE =
            CacheControl.noCache().cachePublic();

    private final FlagService flagService;
    private final PublicMapCache cache;

    public PublicFlagController(FlagService flagService, PublicMapCache cache) {
        this.flagService = flagService;
        this.cache = cache;
    }

    @GetMapping
    @Operation(
            summary = "Listar sinalizações",
            description = "Por padrão devolve as bandeiras abertas e dentro da validade, da mais "
                    + "recente para a mais antiga. Lançamento de setor tem pin no centroide do "
                    + "setor; lançamento da praia inteira tem pin em um ponto interno da área. "
                    + "Com includeExpired=true (exige beachId), devolve o último lançamento de "
                    + "cada escopo, vigente ou não, para a ficha mostrar a última cor com o selo "
                    + "de desatualizada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sinalizações",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = PublicFlag.class)))),
            @ApiResponse(responseCode = "400", description = "includeExpired sem beachId (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<List<PublicFlag>> list(
            @Parameter(description = "Área de banho específica")
            @RequestParam(required = false) UUID beachId,
            @Parameter(description = "Inclui o último lançamento de cada escopo mesmo vencido "
                    + "ou encerrado; exige beachId", example = "false")
            @RequestParam(required = false, defaultValue = "false") boolean includeExpired) {
        List<PublicFlag> flags = cache.get(
                "flags:" + beachId + ":" + includeExpired,
                () -> flagService.publicFlags(beachId, includeExpired));
        return ResponseEntity.ok()
                .cacheControl(CACHE)
                .body(flags);
    }
}
