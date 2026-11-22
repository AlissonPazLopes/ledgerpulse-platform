package dev.ledgerpulse.core.payment.service;

import dev.ledgerpulse.core.payment.domain.RiskDecision;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Week 2 deterministic risk stub before integrating Python risk-engine-ai in Week 3.
 */
@Component
public class StubRiskEvaluator implements RiskEvaluator {

    @Override
    public RiskAssessment evaluate(RiskEvaluationInput input) {
        return new RiskAssessment(0, RiskDecision.APPROVE, List.of());
    }
}
