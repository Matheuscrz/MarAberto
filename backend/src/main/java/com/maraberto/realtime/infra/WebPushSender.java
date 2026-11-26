package com.maraberto.realtime.infra;

import com.maraberto.realtime.domain.PushSubscription;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Envio de notificações Web Push com assinatura VAPID (RFC 8292).
 */
@Component
public class WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final PushProperties pushProperties;
    private final PushSubscriptionRepository repository;
    private final WebPushEndpointValidator endpointValidator;
    private volatile PushService pushService;

    public WebPushSender(
            PushProperties pushProperties,
            PushSubscriptionRepository repository,
            WebPushEndpointValidator endpointValidator) {
        this.pushProperties = pushProperties;
        this.repository = repository;
        this.endpointValidator = endpointValidator;
    }

    public void send(PushSubscription subscription, String jsonPayload) {
        PushService service = getOrCreatePushService();
        if (service == null) {
            return;
        }
        String endpoint = endpointValidator.validateForSend(subscription.endpoint());
        try {
            Notification notification = new Notification(
                    endpoint,
                    subscription.p256dh(),
                    subscription.auth(),
                    jsonPayload.getBytes(StandardCharsets.UTF_8));
            HttpResponse response = service.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 404 || statusCode == 410) {
                log.info("Inscrição Web Push expirada (HTTP {}), removendo", statusCode);
                repository.deleteByEndpoint(subscription.endpoint());
                return;
            }
            if (statusCode >= 400) {
                throw new IllegalStateException("Falha ao enviar Web Push: HTTP " + statusCode);
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Erro ao enviar Web Push", exception);
        }
    }

    private PushService getOrCreatePushService() {
        var vapid = pushProperties.vapid();
        if (!vapid.isConfigured()) {
            return null;
        }
        PushService current = this.pushService;
        if (current != null) {
            return current;
        }
        try {
            PushService created = new PushService(vapid.publicKey(), vapid.privateKey(), vapid.subject());
            this.pushService = created;
            return created;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Chaves VAPID inválidas", exception);
        }
    }
}
