package com.maraberto.realtime.api;

import com.maraberto.realtime.domain.PushChannel;
import com.maraberto.realtime.infra.PushProperties;
import com.maraberto.realtime.infra.PushSubscriptionRepository;
import com.maraberto.shared.security.sanitization.Sanitized;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Registro de inscrições Web Push (VAPID) e tokens FCM do operador para alerta
 * de SOS mesmo com o app ou aba fechados (RF23, ADR-018).
 */
@RestController
@RequestMapping("/api/v1/push")
@Tag(name = "Notificações Push", description = "Inscrições Web Push (VAPID) e tokens FCM dos operacionais")
@SecurityRequirement(name = "bearerAuth")
public class PushSubscriptionController {

    private static final String FCM_ENDPOINT_PREFIX = "fcm://";

    private final PushSubscriptionRepository repository;
    private final PushProperties pushProperties;

    public PushSubscriptionController(
            PushSubscriptionRepository repository,
            PushProperties pushProperties) {
        this.repository = repository;
        this.pushProperties = pushProperties;
    }

    @GetMapping("/vapid-public-key")
    @Operation(summary = "Chave pública VAPID",
            description = "Retorna a chave pública VAPID para inscrição no PushManager do navegador.")
    public VapidPublicKeyResponse vapidPublicKey() {
        var vapid = pushProperties.vapid();
        if (!vapid.isConfigured()) {
            return new VapidPublicKeyResponse(false, null);
        }
        return new VapidPublicKeyResponse(true, vapid.publicKey());
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Registrar inscrição de push",
            description = "Salva ou atualiza a inscrição Web Push (endpoint + chaves) ou o token FCM do operador.")
    public ResponseEntity<Void> register(
            Authentication authentication,
            @Valid @RequestBody RegisterPushSubscriptionRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        if (request.channel() == PushChannel.WEB_PUSH) {
            if (isBlank(request.endpoint()) || isBlank(request.p256dh()) || isBlank(request.auth())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Inscrição WEB_PUSH exige endpoint, p256dh e auth.");
            }
            repository.upsert(userId, PushChannel.WEB_PUSH, request.endpoint().trim(),
                    request.p256dh().trim(), request.auth().trim());
            return ResponseEntity.noContent().build();
        }
        if (isBlank(request.token())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Inscrição FCM exige token.");
        }
        repository.upsert(userId, PushChannel.FCM, FCM_ENDPOINT_PREFIX + request.token().trim(), null, null);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remover inscrição de push",
            description = "Remove a inscrição Web Push ou o token FCM do operador no logout.")
    public ResponseEntity<Void> unregister(
            Authentication authentication,
            @Valid @RequestBody UnregisterPushSubscriptionRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        if (!isBlank(request.endpoint())) {
            repository.deleteByUserAndEndpoint(userId, request.endpoint().trim());
        } else if (!isBlank(request.token())) {
            repository.deleteByUserAndEndpoint(userId, FCM_ENDPOINT_PREFIX + request.token().trim());
        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe endpoint ou token para remover a inscrição.");
        }
        return ResponseEntity.noContent().build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record VapidPublicKeyResponse(boolean enabled, String publicKey) {
    }

    public record RegisterPushSubscriptionRequest(
            @NotNull PushChannel channel,
            @Sanitized String endpoint,
            @Sanitized String p256dh,
            @Sanitized String auth,
            @Sanitized String token) {
    }

    public record UnregisterPushSubscriptionRequest(
            @Sanitized String endpoint,
            @Sanitized String token) {
    }
}
