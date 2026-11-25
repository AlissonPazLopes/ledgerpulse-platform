package dev.ledgerpulse.core.observability;

import dev.ledgerpulse.core.payment.domain.PaymentMethod;
import dev.ledgerpulse.core.payment.domain.PaymentStatus;
import dev.ledgerpulse.core.payment.domain.RiskDecision;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class LedgerCoreMetrics {

    private static final String RISK_ENGINE_CB_NAME = "riskEngine";

    private final MeterRegistry meterRegistry;
    private final Timer riskEvaluationTimer;

    public LedgerCoreMetrics(
            MeterRegistry meterRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.meterRegistry = meterRegistry;
        this.riskEvaluationTimer = Timer.builder("risk_evaluation_latency")
                .description("Latency of risk-engine-ai evaluation calls")
                .publishPercentileHistogram(true)
                .register(meterRegistry);

        CircuitBreaker riskEngineBreaker = circuitBreakerRegistry.circuitBreaker(RISK_ENGINE_CB_NAME);
        Gauge.builder("circuit_breaker_state", riskEngineBreaker, LedgerCoreMetrics::mapCircuitBreakerState)
                .description("Resilience4j circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN, 3=OTHER)")
                .tag("name", RISK_ENGINE_CB_NAME)
                .register(meterRegistry);
    }

    public void recordPaymentProcessed(
            PaymentStatus status,
            PaymentMethod method,
            RiskDecision decision
    ) {
        String statusTag = status != null ? status.name() : "UNKNOWN";
        String methodTag = method != null ? method.name() : "UNKNOWN";
        String decisionTag = decision != null ? decision.name() : "UNKNOWN";

        Counter.builder("ledger_payments_total")
                .description("Total payments processed by status, method and risk decision")
                .tag("status", statusTag)
                .tag("method", methodTag)
                .tag("decision", decisionTag)
                .register(meterRegistry)
                .increment();
    }

    public void recordRefundProcessed(PaymentStatus resultingStatus) {
        String statusTag = resultingStatus != null ? resultingStatus.name() : "UNKNOWN";
        Counter.builder("ledger_refunds_total")
                .description("Total compensating refunds processed by resulting payment status")
                .tag("status", statusTag)
                .register(meterRegistry)
                .increment();
    }

    public <T> T recordRiskEvaluation(Supplier<T> evaluation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return evaluation.get();
        } finally {
            sample.stop(riskEvaluationTimer);
        }
    }

    static double mapCircuitBreakerState(CircuitBreaker circuitBreaker) {
        return switch (circuitBreaker.getState()) {
            case CLOSED -> 0.0;
            case OPEN -> 1.0;
            case HALF_OPEN -> 2.0;
            default -> 3.0;
        };
    }
}
