package dev.ledgerpulse.core.payment.service;

import dev.ledgerpulse.core.payment.domain.RiskDecision;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic stub risk evaluator used when app.risk-engine.mode=stub.
 */
@Component
@ConditionalOnProperty(name = "app.risk-engine.mode", havingValue = "stub")
public class StubRiskEvaluator implements RiskEvaluator {

    @Override
    public RiskAssessment evaluate(RiskEvaluationInput input) {
        return new RiskAssessment(0, RiskDecision.APPROVE, List.of());
    }
}
