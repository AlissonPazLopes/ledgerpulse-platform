package dev.ledgerpulse.core.risk;

import dev.ledgerpulse.core.auth.service.DevIdentitySeeder;
import dev.ledgerpulse.core.outbox.OutboxEvent;
import dev.ledgerpulse.core.outbox.OutboxEventRepository;
import dev.ledgerpulse.core.payment.api.CreatePaymentRequest;
import dev.ledgerpulse.core.payment.domain.PaymentMethod;
import dev.ledgerpulse.core.payment.domain.PaymentStatus;
import dev.ledgerpulse.core.payment.domain.RiskDecision;
import dev.ledgerpulse.core.payment.service.PaymentService;
import dev.ledgerpulse.core.payment.service.RiskEvaluator;
import dev.ledgerpulse.core.security.AuthenticatedMerchant;
import dev.ledgerpulse.core.auth.domain.MerchantStatus;
import dev.ledgerpulse.core.web.CorrelationIdFilter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RiskEngineClientResilienceTest {

    private static MockWebServer mockWebServer;

    @BeforeAll
    static void startServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @DynamicPropertySource
    static void configureRiskEngine(DynamicPropertyRegistry registry) {
        registry.add("app.risk-engine.mode", () -> "http");
        registry.add("app.risk-engine.base-url", () -> mockWebServer.url("/").toString().replaceAll("/$", ""));
        registry.add("app.risk-engine.connect-timeout-ms", () -> "500");
        registry.add("app.risk-engine.read-timeout-ms", () -> "1500");
    }

    @Autowired
    private RiskEvaluator riskEvaluator;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void resetCircuitBreakerAndServerQueue() throws InterruptedException {
        circuitBreakerRegistry.circuitBreaker("riskEngine").reset();
        mockWebServer.setDispatcher(new QueueDispatcher());
        while (mockWebServer.takeRequest(0, TimeUnit.MILLISECONDS) != null) {
            // drain recorded requests from prior tests
        }
    }

    @Test
    void shouldPropagateCorrelationIdAndParseApproveResponse() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "risk_score": 12,
                          "decision": "APPROVE",
                          "reason_codes": [],
                          "model_version": "v1.0.0"
                        }
                        """));

        String corrId = "4f3c9f62-8b9a-4f3b-9d1a-0192837465ab";
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, corrId);
        try {
            RiskEvaluator.RiskAssessment result = riskEvaluator.evaluate(sampleInput());
            assertThat(result.riskScore()).isEqualTo(12);
            assertThat(result.decision()).isEqualTo(RiskDecision.APPROVE);
            assertThat(result.reasonCodes()).isEmpty();
        } finally {
            MDC.remove(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        }

        RecordedRequest recorded = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getPath()).isEqualTo("/v1/risk/evaluate");
        assertThat(recorded.getHeader("X-Correlation-Id")).isEqualTo(corrId);
        assertThat(recorded.getBody().readUtf8()).contains("\"amount_cents\":15000");
    }

    @Test
    void shouldRetryShortTransientErrorAndRecoverOnSecondAttempt() {
        int requestsBefore = mockWebServer.getRequestCount();
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"detail\":\"error\"}"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "risk_score": 18,
                          "decision": "APPROVE",
                          "reason_codes": [],
                          "model_version": "v1.0.0"
                        }
                        """));

        RiskEvaluator.RiskAssessment result = riskEvaluator.evaluate(sampleInput());
        assertThat(result.decision()).isEqualTo(RiskDecision.APPROVE);
        assertThat(result.riskScore()).isEqualTo(18);
        assertThat(mockWebServer.getRequestCount() - requestsBefore).isEqualTo(2);
    }

    @Test
    void shouldFallbackToManualReviewOnReadTimeoutExceeding1500Ms() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"risk_score\":10,\"decision\":\"APPROVE\",\"reason_codes\":[],\"model_version\":\"v1.0.0\"}")
                .setBodyDelay(2, TimeUnit.SECONDS));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"risk_score\":10,\"decision\":\"APPROVE\",\"reason_codes\":[],\"model_version\":\"v1.0.0\"}")
                .setBodyDelay(2, TimeUnit.SECONDS));

        RiskEvaluator.RiskAssessment result = riskEvaluator.evaluate(sampleInput());
        assertThat(result.decision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(result.riskScore()).isEqualTo(50);
        assertThat(result.reasonCodes()).containsExactly(ResilientRiskEvaluator.FALLBACK_REASON_CODE);
    }

    @Test
    void shouldOpenCircuitBreakerOnRepeated500AndCreateManualReviewPaymentWithoutCallingNetwork() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("riskEngine");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // 4 calls x 2 retry attempts = 8 server 500 responses -> opens CircuitBreaker (minimumNumberOfCalls=4)
        for (int i = 0; i < 8; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"detail\":\"down\"}"));
        }
        for (int i = 0; i < 4; i++) {
            RiskEvaluator.RiskAssessment fallback = riskEvaluator.evaluate(sampleInput());
            assertThat(fallback.decision()).isEqualTo(RiskDecision.REVIEW);
            assertThat(fallback.reasonCodes()).containsExactly(ResilientRiskEvaluator.FALLBACK_REASON_CODE);
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int requestCountWhenOpened = mockWebServer.getRequestCount();

        var merchantPrincipal = new AuthenticatedMerchant(
                DevIdentitySeeder.DEMO_MERCHANT_ID,
                "Acme Fintech Store",
                250,
                MerchantStatus.ACTIVE
        );
        var auth = new UsernamePasswordAuthenticationToken(merchantPrincipal, null, List.of());

        var execution = paymentService.createPayment(
                new CreatePaymentRequest(
                        null,
                        "payer-cb-fallback",
                        25_000L,
                        "BRL",
                        PaymentMethod.CARD,
                        "Payment during risk engine outage",
                        List.of()
                ),
                "idem-cb-" + UUID.randomUUID(),
                "corr-cb-open",
                auth
        );

        assertThat(execution.body().status()).isEqualTo(PaymentStatus.MANUAL_REVIEW);
        assertThat(execution.body().riskDecision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(execution.body().reasonCodes()).containsExactly(ResilientRiskEvaluator.FALLBACK_REASON_CODE);
        assertThat(execution.body().ledgerTransactionId()).isNull();

        // Short-circuited by OPEN CircuitBreaker without hitting MockWebServer
        assertThat(mockWebServer.getRequestCount()).isEqualTo(requestCountWhenOpened);

        List<OutboxEvent> outboxEvents = outboxEventRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("PAYMENT", execution.body().id());
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("payment.manual_review_required");
    }

    private static RiskEvaluator.RiskEvaluationInput sampleInput() {
        return new RiskEvaluator.RiskEvaluationInput(
                UUID.randomUUID(),
                DevIdentitySeeder.DEMO_MERCHANT_ID,
                "payer-98765",
                15_000L,
                PaymentMethod.CARD
        );
    }
}
