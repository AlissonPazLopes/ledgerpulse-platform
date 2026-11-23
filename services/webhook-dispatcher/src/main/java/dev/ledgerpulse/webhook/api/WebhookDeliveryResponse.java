package dev.ledgerpulse.webhook.api;

import dev.ledgerpulse.webhook.domain.WebhookDeliveryStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookDeliveryResponse(
        UUID id,
        UUID eventId,
        String eventType,
        UUID merchantId,
        UUID endpointId,
        String targetUrl,
        String correlationId,
        WebhookDeliveryStatus status,
        int attemptCount,
        int maxAttempts,
        Integer lastHttpStatus,
        Long lastLatencyMs,
        String lastError,
        String signatureHeader,
        Instant createdAt,
        Instant updatedAt,
        Instant deliveredAt,
        List<WebhookDeliveryAttemptResponse> attempts
) {
}
