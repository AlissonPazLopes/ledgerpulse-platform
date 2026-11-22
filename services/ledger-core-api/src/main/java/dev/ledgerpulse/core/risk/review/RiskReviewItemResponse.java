package dev.ledgerpulse.core.risk.review;

import dev.ledgerpulse.core.payment.domain.PaymentMethod;
import dev.ledgerpulse.core.payment.domain.PaymentStatus;
import dev.ledgerpulse.core.payment.domain.RiskDecision;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RiskReviewItemResponse(
        UUID paymentId,
        UUID merchantId,
        String payerId,
        long amountCents,
        long platformFeeCents,
        long netMerchantCents,
        String currency,
        PaymentMethod paymentMethod,
        String description,
        PaymentStatus status,
        Integer riskScore,
        RiskDecision riskDecision,
        List<String> reasonCodes,
        String correlationId,
        Instant createdAt
) {
}
