package com.maraberto.beaches.api;

import com.maraberto.beaches.application.BathingAreaImportService;
import com.maraberto.beaches.application.ImportMode;
import com.maraberto.beaches.application.ImportRejectedException;
import com.maraberto.beaches.application.ImportReport;
import com.maraberto.shared.api.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Importação/atualização do mapa de áreas de banho pela coordenação.
 *
 * <p>O arquivo vem do QGIS. A primeira chamada deve ser sempre {@code dryRun=true}
 * (padrão); aplicar exige {@code dryRun=false&confirm=true}.
 */
@RestController
@RequestMapping("/api/v1/management/bathing-areas")
@Tag(name = "Áreas de banho", description = "Importação do mapeamento QGIS/KML")
public class BathingAreaImportController {

    private final BathingAreaImportService importService;
    private final String configuredFile;

    public BathingAreaImportController(
            BathingAreaImportService importService,
            @Value("${maraberto.beaches.import.file:}") String configuredFile) {
        this.importService = importService;
        this.configuredFile = configuredFile;
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Importar ou atualizar áreas de banho",
            description = "Lê o KML do QGIS, compara com o banco e devolve o relatório de diff. "
                    + "Sem `dryRun=false&confirm=true` nada é escrito no mapa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relatório da importação",
                    content = @Content(schema = @Schema(implementation = ImportReport.class))),
            @ApiResponse(responseCode = "400", description = "KML/UF inválidos (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "413", description = "Arquivo acima de 5 MB (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ImportReport importKml(
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestParam String state,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean confirm,
            @RequestParam(defaultValue = "UPSERT") ImportMode mode,
            Authentication authentication) {
        if (!dryRun && !confirm) {
            throw new ImportRejectedException(
                    "Para aplicar a importação informe dryRun=false e confirm=true");
        }

        byte[] content;
        String sourceFile;
        if (file != null && !file.isEmpty()) {
            try {
                content = file.getBytes();
            } catch (IOException exception) {
                throw new ImportRejectedException("Não foi possível ler o arquivo enviado");
            }
            sourceFile = file.getOriginalFilename() == null
                    ? "upload.kml"
                    : file.getOriginalFilename();
        } else {
            Path path = Path.of(configuredFile);
            if (configuredFile.isBlank() || !Files.isReadable(path)) {
                throw new ImportRejectedException(
                        "Envie o arquivo KML ou configure maraberto.beaches.import.file");
            }
            try {
                content = Files.readAllBytes(path);
            } catch (IOException exception) {
                throw new ImportRejectedException("Não foi possível ler o KML configurado");
            }
            sourceFile = path.getFileName().toString();
        }

        return importService.importKml(new BathingAreaImportService.ImportCommand(
                content,
                sourceFile,
                state,
                dryRun,
                mode,
                UUID.fromString(authentication.getName())));
    }
}
