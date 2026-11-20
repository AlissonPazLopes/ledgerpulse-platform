package dev.ledgerpulse.core.auth.api;

import dev.ledgerpulse.core.auth.domain.UserAccount;
import dev.ledgerpulse.core.auth.domain.UserRole;
import dev.ledgerpulse.core.security.AuthenticatedOperator;

import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String name,
        String email,
        UserRole role
) {
    public static UserProfileResponse from(UserAccount user) {
        return new UserProfileResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }

    public static UserProfileResponse from(AuthenticatedOperator operator) {
        return new UserProfileResponse(operator.id(), operator.name(), operator.email(), operator.role());
    }
}
