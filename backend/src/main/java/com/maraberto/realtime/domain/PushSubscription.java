package com.maraberto.realtime.domain;

import java.util.UUID;

public record PushSubscription(
        UUID id,
        UUID userId,
        PushChannel channel,
        String endpoint,
        String p256dh,
        String auth) {
}
