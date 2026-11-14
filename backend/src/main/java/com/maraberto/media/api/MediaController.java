package com.maraberto.media.api;

import com.maraberto.media.application.MediaService;
import com.maraberto.media.domain.ImageTooLargeException;
import com.maraberto.media.domain.InvalidImageException;
import com.maraberto.shared.api.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Foto da sinalização: envio pelo operador e leitura pelo mapa público
 * (RF12/ADR-012).
 *
 * <p>O upload passa pelo backend de propósito. É aqui que a imagem é
 * re-codificada — o que remove EXIF e a coordenada do aparelho — e que o
 * tamanho é limitado. A leitura é pública porque a foto aparece na ficha da
 * praia e no mapa; a URL é do próprio MarAberto, não uma URL pré-assinada que
 * expira no meio do turno.
 */
@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Mídia", description = "Fotos da sinalização")
public class MediaController {

    /**
     * A imagem normalizada nunca muda depois de criada: o {@code id} é o
     * endereço do conteúdo. Por isso o cache pode ser longo e imutável.
     */
    private static final CacheControl PUBLIC_CACHE =
            CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable();

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Enviar foto",
            description = "Recebe uma foto JPEG ou PNG, remove os metadados do aparelho, reduz o "
                    + "maior lado para 1600 px e guarda o JPEG resultante. O identificador "
                    + "devolvido é usado no lançamento da sinalização.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Foto guardada",
                    content = @Content(schema = @Schema(implementation = MediaView.class))),
            @ApiResponse(responseCode = "400", description = "Arquivo não é imagem válida (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "413", description = "Arquivo acima do limite (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public MediaView upload(
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ImageTooLargeException(MediaService.MAX_UPLOAD_BYTES);
        }
        return mediaService.upload(UUID.fromString(authentication.getName()), content);
    }

    /**
     * Mesmo upload com o conteúdo em base64.
     *
     * <p>O app empacotado fala com a API por HTTP nativo (ADR-019) e o corpo
     * tem de ser uma string reenviável: multipart não passa por esse caminho e
     * não pode ser repetido depois de um 401. O binário decodificado é o mesmo
     * que o multipart entrega, e o normalizador roda igual.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Enviar foto em base64",
            description = "Alternativa ao multipart para o app empacotado: recebe o arquivo em "
                    + "base64 no JSON e aplica a mesma normalização e os mesmos limites.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Foto guardada",
                    content = @Content(schema = @Schema(implementation = MediaView.class))),
            @ApiResponse(responseCode = "400", description = "Arquivo não é imagem válida (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "413", description = "Arquivo acima do limite (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public MediaView uploadBase64(
            @Valid @RequestBody MediaUploadRequest request,
            Authentication authentication) {
        return mediaService.upload(
                UUID.fromString(authentication.getName()), decode(request.contentBase64()));
    }

    /** Base64 inválido é erro de entrada, não falha do servidor. */
    private static byte[] decode(String contentBase64) {
        if (contentBase64 == null || contentBase64.isBlank()) {
            throw new InvalidImageException();
        }
        // Base64 cresce ~4/3: recusar antes de decodificar evita alocar o
        // binário de um envio claramente acima do limite.
        if (contentBase64.length() > (MediaService.MAX_UPLOAD_BYTES * 4 / 3) + 8) {
            throw new ImageTooLargeException(MediaService.MAX_UPLOAD_BYTES);
        }
        try {
            return java.util.Base64.getDecoder().decode(contentBase64);
        } catch (java.lang.IllegalArgumentException exception) {
            throw new InvalidImageException();
        }
    }

    @GetMapping("/{mediaId}")
    @Operation(
            summary = "Ler foto",
            description = "Devolve o JPEG normalizado. Imutável e cacheável; com If-None-Match "
                    + "correspondente responde 304.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Imagem",
                    content = @Content(mediaType = "image/jpeg")),
            @ApiResponse(responseCode = "304", description = "Imagem não mudou"),
            @ApiResponse(responseCode = "404", description = "Mídia não encontrada (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<byte[]> read(
            @Parameter(description = "Identificador da mídia") @PathVariable UUID mediaId,
            @Parameter(hidden = true)
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
            String ifNoneMatch) {
        MediaService.StoredMedia stored = mediaService.load(mediaId);
        if (matches(ifNoneMatch, stored.etag())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, stored.etag())
                    .cacheControl(PUBLIC_CACHE)
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.asset().contentType()))
                .header(HttpHeaders.ETAG, stored.etag())
                .cacheControl(PUBLIC_CACHE)
                .contentLength(stored.content().length)
                .body(stored.content());
    }

    /** {@code If-None-Match} pode vir com lista, {@code *} e prefixo fraco {@code W/}. */
    static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String value = candidate.trim();
            if (value.startsWith("W/")) {
                value = value.substring(2).trim();
            }
            if ("*".equals(value) || value.equals(etag)) {
                return true;
            }
        }
        return false;
    }
}
