package dev.ledgerpulse.core.risk;

import dev.ledgerpulse.core.payment.domain.RiskDecision;
import dev.ledgerpulse.core.payment.service.RiskEvaluator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.risk-engine.mode", havingValue = "http", matchIfMissing = true)
public class ResilientRiskEvaluator implements RiskEvaluator {

    public static final String FALLBACK_REASON_CODE = "RISK_ENGINE_UNAVAILABLE";
    private static final Logger log = LoggerFactory.getLogger(ResilientRiskEvaluator.class);

    private final RiskEngineHttpClient httpClient;

    public ResilientRiskEvaluator(RiskEngineHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    @CircuitBreaker(name = "riskEngine", fallbackMethod = "fallbackEvaluate")
    @Retry(name = "riskEngine")
    public RiskAssessment evaluate(RiskEvaluationInput input) {
        RiskEvaluateRequestDto request = new RiskEvaluateRequestDto(
                input.paymentId(),
                input.merchantId(),
                input.payerId(),
                input.amountCents(),
                input.paymentMethod().name(),
                "BR",
                1,
                14,
                180
        );
        RiskEvaluateResponseDto response = httpClient.evaluate(request);
        List<String> codes = response.reasonCodes() == null ? List.of() : List.copyOf(response.reasonCodes());
        return new RiskAssessment(response.riskScore(), response.decision(), codes);
    }

    @SuppressWarnings("unused")
    private RiskAssessment fallbackEvaluate(RiskEvaluationInput input, Throwable ex) {
        log.warn(
                "Risk engine fallback triggered for paymentId={} -> MANUAL_REVIEW ({}): {}",
                input.paymentId(),
                FALLBACK_REASON_CODE,
                ex.getMessage()
        );
        return new RiskAssessment(50, RiskDecision.REVIEW, List.of(FALLBACK_REASON_CODE));
    }
}
