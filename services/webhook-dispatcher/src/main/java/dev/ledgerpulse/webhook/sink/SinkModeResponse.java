package dev.ledgerpulse.webhook.sink;

import java.time.Instant;

public record SinkModeResponse(
        SinkMode mode,
        long timeoutDelayMs,
        Instant updatedAt
) {
}
