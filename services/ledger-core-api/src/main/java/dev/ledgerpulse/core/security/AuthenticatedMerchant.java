package dev.ledgerpulse.core.security;

import dev.ledgerpulse.core.auth.domain.MerchantStatus;

import java.util.UUID;

public record AuthenticatedMerchant(
        UUID id,
        String name,
        int defaultFeeBps,
        MerchantStatus status
) {
}
