package dev.ledgerpulse.webhook.sink;

import java.time.Instant;

public record SinkTestResponse(
        boolean received,
        SinkMode mode,
        String eventId,
        Boolean signaturePresent,
        Instant receivedAt,
        String error
) {
}
