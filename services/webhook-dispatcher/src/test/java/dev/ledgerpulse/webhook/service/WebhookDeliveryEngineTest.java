package dev.ledgerpulse.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerpulse.webhook.domain.WebhookDelivery;
import dev.ledgerpulse.webhook.domain.WebhookDeliveryAttempt;
import dev.ledgerpulse.webhook.domain.WebhookDeliveryStatus;
import dev.ledgerpulse.webhook.domain.WebhookEndpoint;
import dev.ledgerpulse.webhook.domain.WebhookEndpointStatus;
import dev.ledgerpulse.webhook.messaging.WebhookEventMessage;
import dev.ledgerpulse.webhook.repository.WebhookDeliveryAttemptRepository;
import dev.ledgerpulse.webhook.repository.WebhookDeliveryRepository;
import dev.ledgerpulse.webhook.repository.WebhookEndpointRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WebhookDeliveryEngineTest {

    @Autowired
    private WebhookDeliveryEngine deliveryEngine;

    @Autowired
    private WebhookEndpointRepository endpointRepository;

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;

    @Autowired
    private WebhookDeliveryAttemptRepository attemptRepository;

    @Autowired
    private HmacSignatureService hmacSignatureService;

    @Autowired
    private ObjectMapper objectMapper;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Test
    void shouldDeliverSignedWebhookOnVirtualThreadAndRespectIdempotency() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String secret = "whsec_engine_success_secret_2026";
        WebhookEndpoint endpoint = endpointRepository.saveAndFlush(new WebhookEndpoint(
                UUID.randomUUID(),
                merchantId,
                mockWebServer.url("/webhook").toString(),
                secret,
                "MockWebServer OK",
                WebhookEndpointStatus.ACTIVE
        ));

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

        UUID eventId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        WebhookEventMessage event = new WebhookEventMessage(
                eventId,
                "payment.settled",
                "1.0",
                Instant.now(),
                correlationId,
                merchantId,
                Map.of("amountCents", 15000, "currency", "BRL")
        );
        String rawJson = objectMapper.writeValueAsString(event);

        List<WebhookDeliveryEngine.DispatchResult> firstResults = deliveryEngine.dispatchEvent(event, rawJson);
        assertThat(firstResults).hasSize(1);
        var result = firstResults.getFirst();
        assertThat(result.status()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.skippedAlreadyDelivered()).isFalse();
        assertThat(result.executedOnVirtualThread()).isTrue();
        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0L);

        RecordedRequest recorded = mockWebServer.takeRequest();
        String sentBody = recorded.getBody().readUtf8();
        String signatureHeader = recorded.getHeader("X-LedgerPulse-Signature");
        assertThat(sentBody).isEqualTo(rawJson);
        assertThat(recorded.getHeader("X-LedgerPulse-Event-Id")).isEqualTo(eventId.toString());
        assertThat(recorded.getHeader("X-Correlation-Id")).isEqualTo(correlationId);
        assertThat(hmacSignatureService.verifySignature(secret, sentBody, signatureHeader)).isTrue();

        // Duplicate delivery of the exact same eventId must be skipped idempotently
        List<WebhookDeliveryEngine.DispatchResult> duplicateResults = deliveryEngine.dispatchEvent(event, rawJson);
        assertThat(duplicateResults).hasSize(1);
        assertThat(duplicateResults.getFirst().skippedAlreadyDelivered()).isTrue();
        assertThat(duplicateResults.getFirst().status()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);

        WebhookDelivery persisted = deliveryRepository.findByEventIdAndEndpointId(eventId, endpoint.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(persisted.getAttemptCount()).isEqualTo(1);
        assertThat(persisted.getDeliveredAt()).isNotNull();

        List<WebhookDeliveryAttempt> attempts = attemptRepository.findByDeliveryIdOrderByAttemptNumberAsc(persisted.getId());
        assertThat(attempts).hasSize(1);
        assertThat(attempts.getFirst().getHttpStatus()).isEqualTo(200);
        assertThat(attempts.getFirst().getSignatureHeader()).isEqualTo(signatureHeader);
    }

    @Test
    void shouldTransitionFromRetryingToDlqAfterFour500FailuresAndPersistAllAttempts() throws Exception {
        UUID merchantId = UUID.randomUUID();
        WebhookEndpoint endpoint = endpointRepository.saveAndFlush(new WebhookEndpoint(
                UUID.randomUUID(),
                merchantId,
                mockWebServer.url("/webhook-fail").toString(),
                "whsec_engine_retry_secret_2026",
                "MockWebServer 500",
                WebhookEndpointStatus.ACTIVE
        ));

        for (int i = 0; i < 4; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("error"));
        }

        UUID eventId = UUID.randomUUID();
        WebhookEventMessage event = new WebhookEventMessage(
                eventId,
                "payment.declined",
                "1.0",
                Instant.now(),
                UUID.randomUUID().toString(),
                merchantId,
                Map.of("amountCents", 9900)
        );
        String rawJson = objectMapper.writeValueAsString(event);

        for (int attempt = 1; attempt <= 3; attempt++) {
            var res = deliveryEngine.dispatchEvent(event, rawJson).getFirst();
            assertThat(res.status()).isEqualTo(WebhookDeliveryStatus.RETRYING);
            assertThat(res.attemptNumber()).isEqualTo(attempt);
            assertThat(res.httpStatus()).isEqualTo(500);
        }

        var finalRes = deliveryEngine.dispatchEvent(event, rawJson).getFirst();
        assertThat(finalRes.status()).isEqualTo(WebhookDeliveryStatus.DLQ);
        assertThat(finalRes.attemptNumber()).isEqualTo(4);
        assertThat(finalRes.httpStatus()).isEqualTo(500);

        WebhookDelivery persisted = deliveryRepository.findByEventIdAndEndpointId(eventId, endpoint.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(WebhookDeliveryStatus.DLQ);
        assertThat(persisted.getAttemptCount()).isEqualTo(4);
        assertThat(persisted.getLastHttpStatus()).isEqualTo(500);
        assertThat(persisted.getLastError()).contains("500");

        List<WebhookDeliveryAttempt> attempts = attemptRepository.findByDeliveryIdOrderByAttemptNumberAsc(persisted.getId());
        assertThat(attempts).hasSize(4);
        assertThat(attempts).extracting(WebhookDeliveryAttempt::getAttemptNumber).containsExactly(1, 2, 3, 4);
    }
}
