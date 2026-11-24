package dev.ledgerpulse.core.stream;

import java.time.Instant;

public record SseControlEvent(
        String eventType,
        Instant timestamp,
        String message
) {
}
