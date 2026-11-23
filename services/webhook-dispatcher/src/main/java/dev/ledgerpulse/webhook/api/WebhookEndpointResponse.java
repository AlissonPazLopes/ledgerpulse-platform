package dev.ledgerpulse.webhook.api;

import dev.ledgerpulse.webhook.domain.WebhookEndpointStatus;

import java.time.Instant;
import java.util.UUID;

public record WebhookEndpointResponse(
        UUID id,
        UUID merchantId,
        String targetUrl,
        String secretPreview,
        String description,
        WebhookEndpointStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
