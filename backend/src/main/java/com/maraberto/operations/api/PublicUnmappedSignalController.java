package com.maraberto.operations.api;

import com.maraberto.operations.application.UnmappedSignalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alertas de sinalização em área não mapeada no mapa público (RF12/RF38).
 *
 * <p>O guarda-vidas pode estar numa praia nova, num trecho de rio sem KML ou
 * numa barragem que passou a receber banhistas. Enquanto a coordenação não
 * identifica o local, o banhista vê o pin com a cor lançada e o aviso de que a
 * área não está mapeada. Não é bandeira de praia: não há área, setor nem ficha
 * para herdar, e a triagem continua na fila da coordenação.
 */
@RestController
@RequestMapping("/api/v1/unmapped-signals")
@Tag(name = "Áreas não mapeadas",
        description = "Alertas de sinalização lançada fora do mapeamento")
public class PublicUnmappedSignalController {

    private static final CacheControl CACHE =
            CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic();

    private final UnmappedSignalService service;

    public PublicUnmappedSignalController(UnmappedSignalService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Listar alertas em área não mapeada",
            description = "Devolve os registros pendentes de triagem e dentro da validade, do "
                    + "mais recente para o mais antigo. O pin é o ponto do lançamento e a cor é a "
                    + "que o guarda-vidas lançou; nada aqui pertence a uma praia ou setor.")
    @ApiResponse(responseCode = "200", description = "Alertas vigentes",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = PublicUnmappedSignal.class))))
    public ResponseEntity<List<PublicUnmappedSignal>> list() {
        return ResponseEntity.ok()
                .cacheControl(CACHE)
                .body(service.publicAlerts());
    }
}
