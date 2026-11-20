package dev.ledgerpulse.core.auth.api;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserProfileResponse user
) {
}
