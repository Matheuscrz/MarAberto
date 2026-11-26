package com.maraberto.realtime.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "maraberto.push")
public record PushProperties(
        Vapid vapid,
        Fcm fcm) {

    public PushProperties {
        if (vapid == null) {
            vapid = new Vapid("", "", "mailto:contato@maraberto.dev");
        }
        if (fcm == null) {
            fcm = new Fcm(false, "", "", "");
        }
    }

    public record Vapid(
            String publicKey,
            String privateKey,
            String subject) {

        public boolean isConfigured() {
            return publicKey != null && !publicKey.isBlank()
                    && privateKey != null && !privateKey.isBlank();
        }
    }

    public record Fcm(
            boolean enabled,
            String projectId,
            String credentialsFile,
            String endpoint) {

        public boolean isConfigured() {
            return enabled && ((endpoint != null && !endpoint.isBlank())
                    || (projectId != null && !projectId.isBlank()
                    && credentialsFile != null && !credentialsFile.isBlank()));
        }
    }
}
