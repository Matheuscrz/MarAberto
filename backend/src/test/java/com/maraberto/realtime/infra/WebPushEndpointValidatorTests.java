package com.maraberto.realtime.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Testes de prevenção a SSRF na validação de endpoints Web Push (F-05). */
class WebPushEndpointValidatorTests {

    private final WebPushEndpointValidator publicValidator = new WebPushEndpointValidator(
            host -> new InetAddress[] {InetAddress.getByAddress(host, new byte[] {(byte) 142, (byte) 250, 79, 46})});

    @ParameterizedTest
    @ValueSource(strings = {
            "https://fcm.googleapis.com/fcm/send/abc123",
            "https://updates.push.services.mozilla.com/wpush/v2/xyz",
            "https://wns2-bl2p.notify.windows.com/w/?token=abc",
            "https://web.push.apple.com:443/Q_abc123"
    })
    void acceptsLegitimateHttpsPushEndpoints(String endpoint) {
        assertThat(publicValidator.validateForRegistration(endpoint)).isEqualTo(endpoint);
        assertThat(publicValidator.validateForSend(endpoint)).isEqualTo(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fcm.googleapis.com/fcm/send/abc",
            "https://fcm.googleapis.com:8443/fcm/send/abc",
            "https://user:pass@fcm.googleapis.com/fcm/send/abc",
            "https://localhost/sub",
            "https://sub.localhost/sub",
            "https://garage/sub",
            "https://garage:3900/sub",
            "https://service.internal/sub",
            "https://127.0.0.1/sub",
            "https://169.254.169.254/latest/meta-data/",
            "https://10.0.0.1/sub",
            "https://192.168.1.10/sub",
            "https://[::1]/sub",
            "https://[fe80::1]/sub",
            "ftp://fcm.googleapis.com/file"
    })
    void rejectsSsrfAndInvalidEndpoints(String endpoint) {
        assertThatThrownBy(() -> publicValidator.validateForRegistration(endpoint))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsHostResolvingToPrivateOrMetadataIp() {
        WebPushEndpointValidator loopbackResolver = new WebPushEndpointValidator(
                host -> new InetAddress[] {InetAddress.getByAddress(host, new byte[] {127, 0, 0, 1})});
        assertThatThrownBy(() -> loopbackResolver.validateForRegistration("https://rebind.example.com/sub"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("privada");

        WebPushEndpointValidator metadataResolver = new WebPushEndpointValidator(
                host -> new InetAddress[] {InetAddress.getByAddress(host, new byte[] {(byte) 169, (byte) 254, (byte) 169, (byte) 254})});
        assertThatThrownBy(() -> metadataResolver.validateForSend("https://metadata.example.com/sub"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("privada");

        WebPushEndpointValidator cgnatResolver = new WebPushEndpointValidator(
                host -> new InetAddress[] {InetAddress.getByAddress(host, new byte[] {100, 64, 0, 1})});
        assertThatThrownBy(() -> cgnatResolver.validateForSend("https://cgnat.example.com/sub"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnresolvableHostAtSendTime() {
        WebPushEndpointValidator failingDns = new WebPushEndpointValidator(host -> {
            throw new UnknownHostException(host);
        });
        assertThatThrownBy(() -> failingDns.validateForSend("https://nonexistent.example.com/sub"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não resolvido");
    }
}
