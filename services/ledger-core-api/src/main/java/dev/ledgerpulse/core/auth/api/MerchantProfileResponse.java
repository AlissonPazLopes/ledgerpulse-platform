package dev.ledgerpulse.core.auth.api;

import dev.ledgerpulse.core.auth.domain.MerchantStatus;
import dev.ledgerpulse.core.security.AuthenticatedMerchant;

import java.util.UUID;

public record MerchantProfileResponse(
        UUID id,
        String name,
        int defaultFeeBps,
        MerchantStatus status
) {
    public static MerchantProfileResponse from(AuthenticatedMerchant merchant) {
        return new MerchantProfileResponse(
                merchant.id(),
                merchant.name(),
                merchant.defaultFeeBps(),
                merchant.status()
        );
    }
}
