package dev.ledgerpulse.core.security;

import dev.ledgerpulse.core.auth.domain.UserRole;

import java.util.UUID;

public record AuthenticatedOperator(
        UUID id,
        String name,
        String email,
        UserRole role
) {
}
