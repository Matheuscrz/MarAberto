package com.maraberto.realtime.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.maraberto.realtime.domain.PushSubscription;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Envio de notificações FCM para o app Capacitor Android (app fechado).
 *
 * <p>Em produção exige endpoint/credenciais configurados; quando desabilitado
 * ou sem credenciais, apenas ignora o envio sem impedir o boot nem o STOMP.
 */
@Component
public class FcmPushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final String FCM_ENDPOINT_PREFIX = "fcm://";

    private final PushProperties pushProperties;
    private final PushSubscriptionRepository repository;
    private final RestClient restClient;

    public FcmPushSender(PushProperties pushProperties, PushSubscriptionRepository repository) {
        this.pushProperties = pushProperties;
        this.repository = repository;
        this.restClient = RestClient.create();
    }

    public void send(PushSubscription subscription, JsonNode payload) {
        var fcm = pushProperties.fcm();
        if (!fcm.isConfigured()) {
            return;
        }
        String endpoint = fcm.endpoint();
        if (endpoint == null || endpoint.isBlank()) {
            log.debug("FCM habilitado sem endpoint configurado; envio ignorado");
            return;
        }

        String token = subscription.endpoint().startsWith(FCM_ENDPOINT_PREFIX)
                ? subscription.endpoint().substring(FCM_ENDPOINT_PREFIX.length())
                : subscription.endpoint();
        String status = payload.path("status").asText("DISPATCHED");
        String title = "CANCELLED".equals(status) ? "SOS atendido" : "SOS MarAberto";
        String body = "CANCELLED".equals(status)
                ? "Chamado assumido por outro operador."
                : "Novo chamado de emergência aguardando atendimento.";

        Map<String, Object> message = Map.of(
                "message", Map.of(
                        "token", token,
                        "notification", Map.of("title", title, "body", body),
                        "data", Map.of(
                                "callId", payload.path("callId").asText(""),
                                "dispatchId", payload.path("dispatchId").asText(""),
                                "status", status,
                                "url", "/operador"),
                        "android", Map.of("priority", "high")));

        try {
            restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Gone expired) {
            log.info("Token FCM expirado (HTTP {}), removendo", expired.getStatusCode().value());
            repository.deleteByEndpoint(subscription.endpoint());
        } catch (Exception exception) {
            throw new IllegalStateException("Falha ao enviar push FCM", exception);
        }
    }
}
