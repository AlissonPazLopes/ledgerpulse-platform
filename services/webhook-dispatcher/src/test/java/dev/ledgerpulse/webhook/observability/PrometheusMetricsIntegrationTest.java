package dev.ledgerpulse.webhook.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerpulse.webhook.domain.WebhookDeliveryStatus;
import dev.ledgerpulse.webhook.domain.WebhookEndpoint;
import dev.ledgerpulse.webhook.domain.WebhookEndpointStatus;
import dev.ledgerpulse.webhook.messaging.WebhookEventMessage;
import dev.ledgerpulse.webhook.repository.WebhookEndpointRepository;
import dev.ledgerpulse.webhook.security.AuthenticatedOperator;
import dev.ledgerpulse.webhook.security.JwtService;
import dev.ledgerpulse.webhook.security.UserRole;
import dev.ledgerpulse.webhook.service.WebhookDeliveryEngine;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
class PrometheusMetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebhookEndpointRepository endpointRepository;

    @Autowired
    private WebhookDeliveryEngine deliveryEngine;

    @Autowired
    private JwtService jwtService;

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
    void shouldExposeCustomPrometheusMetricsAfterDeliveryDlqAndReplay() throws Exception {
        UUID merchantId = UUID.randomUUID();
        endpointRepository.saveAndFlush(new WebhookEndpoint(
                UUID.randomUUID(),
                merchantId,
                mockWebServer.url("/sink").toString(),
                "whsec_prometheus_test_secret_2026",
                "Prometheus Test Sink",
                WebhookEndpointStatus.ACTIVE
        ));

        // 1. Successful delivery -> DELIVERED
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));
        WebhookEventMessage okEvent = new WebhookEventMessage(
                UUID.randomUUID(),
                "payment.settled",
                "1.0",
                Instant.now(),
                UUID.randomUUID().toString(),
                merchantId,
                Map.of("amountCents", 10_000L)
        );
        var okResult = deliveryEngine.dispatchEvent(okEvent, objectMapper.writeValueAsString(okEvent)).getFirst();
        assertThat(okResult.status()).isEqualTo(WebhookDeliveryStatus.DELIVERED);

        // 2. Four failures -> DLQ, followed by manual replay -> DELIVERED
        for (int i = 0; i < 4; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        }
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        WebhookEventMessage dlqEvent = new WebhookEventMessage(
                UUID.randomUUID(),
                "payment.refunded",
                "1.0",
                Instant.now(),
                UUID.randomUUID().toString(),
                merchantId,
                Map.of("amountCents", 5_000L)
        );
        String dlqJson = objectMapper.writeValueAsString(dlqEvent);
        UUID dlqDeliveryId = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            dlqDeliveryId = deliveryEngine.dispatchEvent(dlqEvent, dlqJson).getFirst().deliveryId();
        }

        String analystJwt = jwtService.generateToken(new AuthenticatedOperator(
                UUID.randomUUID(),
                "Obs Analyst",
                "obs.analyst@ledgerpulse.dev",
                UserRole.ANALYST
        ));
        mockMvc.perform(post("/api/v1/webhooks/dlq/" + dlqDeliveryId + "/replay")
                        .header("Authorization", "Bearer " + analystJwt))
                .andExpect(status().isOk());

        String prometheusBody = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(prometheusBody).contains("webhook_deliveries_total");
        assertThat(prometheusBody).contains("webhook_dlq_total");
        assertThat(prometheusBody).contains("webhook_dlq_replays_total");
        assertThat(prometheusBody).contains("webhook_delivery_latency_seconds_bucket");
        assertThat(prometheusBody).contains("webhook_delivery_latency_seconds_sum");
        assertThat(prometheusBody).contains("webhook_delivery_latency_seconds_count");
    }
}
