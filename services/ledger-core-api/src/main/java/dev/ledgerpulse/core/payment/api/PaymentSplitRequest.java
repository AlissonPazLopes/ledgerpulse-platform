package dev.ledgerpulse.core.payment.api;

import dev.ledgerpulse.core.payment.domain.SplitType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaymentSplitRequest(
        @NotNull(message = "must not be null")
        UUID recipientAccountId,

        @NotNull(message = "must not be null")
        SplitType splitType,

        @Min(value = 1, message = "must be between 1 and 10000")
        @Max(value = 10000, message = "must be between 1 and 10000")
        Integer percentageBps,

        @Min(value = 1, message = "must be greater than 0")
        Long amountCents
) {
}
