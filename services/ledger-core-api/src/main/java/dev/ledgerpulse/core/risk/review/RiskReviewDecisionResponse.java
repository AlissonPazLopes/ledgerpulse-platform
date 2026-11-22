package dev.ledgerpulse.core.risk.review;

import dev.ledgerpulse.core.payment.domain.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record RiskReviewDecisionResponse(
        UUID decisionId,
        UUID paymentId,
        RiskReviewDecisionType decision,
        PaymentStatus paymentStatus,
        UUID ledgerTransactionId,
        String justification,
        UUID reviewedByUserId,
        String reviewedByEmail,
        Instant reviewedAt
) {
}
