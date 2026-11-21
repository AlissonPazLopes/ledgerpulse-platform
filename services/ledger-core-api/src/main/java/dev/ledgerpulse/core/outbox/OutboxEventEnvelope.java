package dev.ledgerpulse.core.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OutboxEventEnvelope(
        UUID eventId,
        String eventType,
        String schemaVersion,
        Instant occurredAt,
        String correlationId,
        UUID merchantId,
        Map<String, Object> payload
) {
}
