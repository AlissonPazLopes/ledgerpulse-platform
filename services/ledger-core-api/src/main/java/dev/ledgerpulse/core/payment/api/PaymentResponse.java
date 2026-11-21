package dev.ledgerpulse.core.payment.api;

import dev.ledgerpulse.core.payment.domain.PaymentMethod;
import dev.ledgerpulse.core.payment.domain.PaymentStatus;
import dev.ledgerpulse.core.payment.domain.RiskDecision;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
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
        UUID ledgerTransactionId,
        String idempotencyKey,
        String correlationId,
        List<PaymentSplitResponse> splits,
        Instant createdAt,
        Instant updatedAt
) {
}
