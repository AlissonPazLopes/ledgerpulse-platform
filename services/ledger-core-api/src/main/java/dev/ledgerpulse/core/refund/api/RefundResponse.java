package dev.ledgerpulse.core.refund.api;

import dev.ledgerpulse.core.payment.domain.PaymentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RefundResponse(
        UUID refundId,
        UUID paymentId,
        UUID ledgerTransactionId,
        long amountCents,
        long platformFeeReversedCents,
        long netMerchantReversedCents,
        List<RecipientReversalDto> recipientReversals,
        PaymentStatus paymentStatus,
        long totalRefundedCents,
        long remainingRefundableCents,
        String reason,
        String idempotencyKey,
        String correlationId,
        Instant createdAt
) {
}
