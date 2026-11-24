package dev.ledgerpulse.core.refund.api;

import java.util.UUID;

public record RecipientReversalDto(
        UUID recipientAccountId,
        long amountCents
) {
}
