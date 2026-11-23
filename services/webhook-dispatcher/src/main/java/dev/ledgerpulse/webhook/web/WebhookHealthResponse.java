package dev.ledgerpulse.webhook.web;

import java.time.Instant;

public record WebhookHealthResponse(
        String status,
        String service,
        String version,
        boolean virtualThreadsEnabled,
        Instant timestamp
) {
}
