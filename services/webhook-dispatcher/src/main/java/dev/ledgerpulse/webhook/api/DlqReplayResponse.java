package dev.ledgerpulse.webhook.api;

import dev.ledgerpulse.webhook.domain.WebhookDeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public record DlqReplayResponse(
        UUID deliveryId,
        UUID eventId,
        WebhookDeliveryStatus status,
        Instant replayedAt,
        Integer httpStatus,
        long latencyMs,
        String errorMessage
) {
}
