package dev.ledgerpulse.core.auth.api;

import java.time.Instant;

public record AdminStatusResponse(
        String status,
        String scope,
        Instant checkedAt
) {
}
