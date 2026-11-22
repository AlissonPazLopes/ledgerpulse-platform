package dev.ledgerpulse.core.payment.api;

import dev.ledgerpulse.core.payment.domain.PaymentSplit;
import dev.ledgerpulse.core.payment.domain.SplitType;

import java.util.UUID;

public record PaymentSplitResponse(
        UUID recipientAccountId,
        SplitType splitType,
        Integer percentageBps,
        long amountCents
) {
    public static PaymentSplitResponse from(PaymentSplit split) {
        return new PaymentSplitResponse(
                split.getRecipientAccountId(),
                split.getSplitType(),
                split.getPercentageBps(),
                split.getAmountCents()
        );
    }
}
