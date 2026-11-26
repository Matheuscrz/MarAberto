package com.maraberto.realtime.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.realtime.domain.PushChannel;
import com.maraberto.realtime.domain.PushSubscription;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Despacha notificações push (Web Push VAPID e FCM) para todas as inscrições
 * registradas do operador destinatário.
 */
@Component
public class SosPushDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SosPushDispatcher.class);

    private final PushSubscriptionRepository repository;
    private final WebPushSender webPushSender;
    private final FcmPushSender fcmPushSender;
    private final ObjectMapper objectMapper;

    public SosPushDispatcher(
            PushSubscriptionRepository repository,
            WebPushSender webPushSender,
            FcmPushSender fcmPushSender,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.webPushSender = webPushSender;
        this.fcmPushSender = fcmPushSender;
        this.objectMapper = objectMapper;
    }

    public void dispatch(UUID userId, Object rawPayload) {
        List<PushSubscription> subscriptions = repository.findByUserId(userId);
        if (subscriptions.isEmpty()) {
            return;
        }
        JsonNode payload = objectMapper.valueToTree(rawPayload);
        String webPushJson = toRedactedPushJson(payload);

        RuntimeException lastError = null;
        for (PushSubscription subscription : subscriptions) {
            try {
                if (subscription.channel() == PushChannel.WEB_PUSH) {
                    webPushSender.send(subscription, webPushJson);
                } else if (subscription.channel() == PushChannel.FCM) {
                    fcmPushSender.send(subscription, payload);
                }
            } catch (RuntimeException exception) {
                lastError = exception;
                log.warn("Falha no envio de push ({}) para usuário {}: {}",
                        subscription.channel(), userId, exception.getMessage());
            }
        }
        if (lastError != null) {
            throw lastError;
        }
    }

    public void dispatchBestEffort(UUID userId, Object rawPayload) {
        try {
            dispatch(userId, rawPayload);
        } catch (RuntimeException exception) {
            log.warn("Falha best-effort no envio de push para usuário {}: {}",
                    userId, exception.getMessage());
        }
    }

    private String toRedactedPushJson(JsonNode payload) {
        Map<String, Object> body = Map.of(
                "callId", payload.path("callId").asText(""),
                "dispatchId", payload.path("dispatchId").asText(""),
                "round", payload.path("round").asInt(1),
                "status", payload.path("status").asText("DISPATCHED"),
                "url", "/operador");
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Erro ao serializar payload de push", exception);
        }
    }
}
