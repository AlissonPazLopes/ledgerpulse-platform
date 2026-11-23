package dev.ledgerpulse.webhook.security;

import java.util.UUID;

public record AuthenticatedOperator(
        UUID id,
        String name,
        String email,
        UserRole role
) {
}
