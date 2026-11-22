package dev.ledgerpulse.core.payment.service;

import dev.ledgerpulse.core.payment.domain.PaymentMethod;
import dev.ledgerpulse.core.payment.domain.RiskDecision;

import java.util.List;
import java.util.UUID;

public interface RiskEvaluator {

    RiskAssessment evaluate(RiskEvaluationInput input);

    record RiskEvaluationInput(
            UUID paymentId,
            UUID merchantId,
            String payerId,
            long amountCents,
            PaymentMethod paymentMethod
    ) {
    }

    record RiskAssessment(
            int riskScore,
            RiskDecision decision,
            List<String> reasonCodes
    ) {
    }
}
