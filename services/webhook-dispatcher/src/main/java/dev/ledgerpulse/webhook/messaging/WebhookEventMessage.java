package dev.ledgerpulse.webhook.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WebhookEventMessage(
        UUID eventId,
        String eventType,
        String schemaVersion,
        Instant occurredAt,
        String correlationId,
        UUID merchantId,
        Map<String, Object> payload
) {
}
