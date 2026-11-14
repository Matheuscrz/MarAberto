package com.maraberto.beaches.api;

import com.maraberto.beaches.application.BathingAreaCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lista de áreas de banho ativas para a coordenação.
 *
 * <p>Diferente da lista pública, que exige uma posição e devolve distância, esta
 * é a relação completa para escolher uma área num formulário — por exemplo, ao
 * identificar uma sinalização registrada fora do mapeamento.
 */
@RestController
@RequestMapping("/api/v1/management/bathing-areas")
@Tag(name = "Áreas de banho", description = "Importação do mapeamento QGIS/KML")
@SecurityRequirement(name = "bearerAuth")
public class ManagementBathingAreaController {

    private final BathingAreaCatalogService catalogService;

    public ManagementBathingAreaController(BathingAreaCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @Operation(
            summary = "Listar áreas de banho ativas",
            description = "Relação completa das áreas ativas, ordenada por município e nome, "
                    + "com a quantidade de setores ativos.")
    @ApiResponse(responseCode = "200", description = "Áreas ativas",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = BathingAreaOption.class))))
    public List<BathingAreaOption> list() {
        return catalogService.activeAreas();
    }
}
