package dev.ledgerpulse.webhook.api;

import java.time.Instant;

public record WebhookDeliveryAttemptResponse(
        int attemptNumber,
        Integer httpStatus,
        long latencyMs,
        String signatureHeader,
        String errorMessage,
        Instant attemptedAt
) {
}
