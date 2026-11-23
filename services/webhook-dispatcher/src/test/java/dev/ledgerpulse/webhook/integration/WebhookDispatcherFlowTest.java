package dev.ledgerpulse.webhook.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerpulse.webhook.domain.WebhookDeliveryStatus;
import dev.ledgerpulse.webhook.messaging.RetryEscalationPolicy;
import dev.ledgerpulse.webhook.messaging.WebhookEventMessage;
import dev.ledgerpulse.webhook.security.AuthenticatedOperator;
import dev.ledgerpulse.webhook.security.JwtService;
import dev.ledgerpulse.webhook.security.UserRole;
import dev.ledgerpulse.webhook.service.WebhookDeliveryEngine;
import dev.ledgerpulse.webhook.sink.SinkMode;
import dev.ledgerpulse.webhook.sink.WebhookSinkStateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebhookDispatcherFlowTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebhookDeliveryEngine deliveryEngine;

    @Autowired
    private RetryEscalationPolicy retryEscalationPolicy;

    @Autowired
    private WebhookSinkStateService sinkStateService;

    @BeforeEach
    void setUp() {
        sinkStateService.updateMode(SinkMode.OK_200);
    }

    @AfterEach
    void tearDown() {
        sinkStateService.updateMode(SinkMode.OK_200);
    }

    @Test
    void shouldExhaustFailuresIntoDlqAndDeliverAfterSwitchingSinkModeAndManualReplay() throws Exception {
        String adminJwt = jwtFor(UserRole.ADMIN, "admin.flow@ledgerpulse.dev");
        String analystJwt = jwtFor(UserRole.ANALYST, "analyst.flow@ledgerpulse.dev");

        UUID merchantId = UUID.randomUUID();
        String sinkUrl = "http://localhost:" + port + "/api/v1/sink/test";

        // 1. Register merchant endpoint pointing at the live Merchant Webhook Sink on RANDOM_PORT
        ResponseEntity<JsonNode> createEndpointRes = restTemplate.exchange(
                "/api/v1/webhooks/endpoints",
                HttpMethod.POST,
                jsonEntity(adminJwt, Map.of(
                        "merchantId", merchantId.toString(),
                        "targetUrl", sinkUrl,
                        "secret", "whsec_live_flow_secret_2026",
                        "description", "Live Flow Sink Endpoint"
                )),
                JsonNode.class
        );
        assertThat(createEndpointRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createEndpointRes.getBody()).isNotNull();
        assertThat(createEndpointRes.getBody().get("secretPreview").asText()).isEqualTo("whsec_****2026");

        // 2. Switch Sink to FAIL_500
        ResponseEntity<JsonNode> failModeRes = restTemplate.exchange(
                "/api/v1/sink/mode",
                HttpMethod.PUT,
                jsonEntity(analystJwt, Map.of("mode", "FAIL_500")),
                JsonNode.class
        );
        assertThat(failModeRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(failModeRes.getBody().get("mode").asText()).isEqualTo("FAIL_500");

        // 3. Dispatch event across 1 initial + 3 retries = 4 attempts until exhaustion into DLQ
        UUID eventId = UUID.randomUUID();
        WebhookEventMessage event = new WebhookEventMessage(
                eventId,
                "payment.settled",
                "1.0",
                Instant.now(),
                UUID.randomUUID().toString(),
                merchantId,
                Map.of("amountCents", 25000, "currency", "BRL")
        );
        String rawJson = objectMapper.writeValueAsString(event);

        UUID deliveryId = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            var route = retryEscalationPolicy.decideForRetryCount(attempt - 1L);
            var dispatchResults = deliveryEngine.dispatchEvent(event, rawJson, route.isDlq());
            assertThat(dispatchResults).hasSize(1);
            var result = dispatchResults.getFirst();
            deliveryId = result.deliveryId();
            assertThat(result.httpStatus()).isEqualTo(500);
            if (attempt < 4) {
                assertThat(result.status()).isEqualTo(WebhookDeliveryStatus.RETRYING);
                assertThat(route.target()).isEqualTo(RetryEscalationPolicy.Target.RETRY);
            } else {
                assertThat(result.status()).isEqualTo(WebhookDeliveryStatus.DLQ);
                assertThat(route.isDlq()).isTrue();
            }
        }
        assertThat(deliveryId).isNotNull();

        // 4. Verify GET /api/v1/webhooks/deliveries shows DLQ with 4 ordered attempts
        ResponseEntity<JsonNode> dlqListRes = restTemplate.exchange(
                "/api/v1/webhooks/deliveries?status=DLQ&merchantId=" + merchantId + "&eventId=" + eventId,
                HttpMethod.GET,
                authEntity(analystJwt),
                JsonNode.class
        );
        assertThat(dlqListRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode dlqItems = dlqListRes.getBody().get("items");
        assertThat(dlqItems).hasSize(1);
        assertThat(dlqItems.get(0).get("status").asText()).isEqualTo("DLQ");
        assertThat(dlqItems.get(0).get("attemptCount").asInt()).isEqualTo(4);
        assertThat(dlqItems.get(0).get("attempts")).hasSize(4);

        // 5. Switch Sink back to OK_200 and execute manual DLQ replay
        restTemplate.exchange(
                "/api/v1/sink/mode",
                HttpMethod.PUT,
                jsonEntity(analystJwt, Map.of("mode", "OK_200")),
                JsonNode.class
        );

        ResponseEntity<JsonNode> replayRes = restTemplate.exchange(
                "/api/v1/webhooks/dlq/" + deliveryId + "/replay",
                HttpMethod.POST,
                authEntity(analystJwt),
                JsonNode.class
        );
        assertThat(replayRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayRes.getBody().get("deliveryId").asText()).isEqualTo(deliveryId.toString());
        assertThat(replayRes.getBody().get("status").asText()).isEqualTo("DELIVERED");
        assertThat(replayRes.getBody().get("httpStatus").asInt()).isEqualTo(200);
        assertThat(replayRes.getBody().get("latencyMs").asLong()).isGreaterThanOrEqualTo(0L);

        // 6. Confirm delivery is now DELIVERED with 5 total attempts
        ResponseEntity<JsonNode> deliveredRes = restTemplate.exchange(
                "/api/v1/webhooks/deliveries?eventId=" + eventId,
                HttpMethod.GET,
                authEntity(analystJwt),
                JsonNode.class
        );
        assertThat(deliveredRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode deliveredItem = deliveredRes.getBody().get("items").get(0);
        assertThat(deliveredItem.get("status").asText()).isEqualTo("DELIVERED");
        assertThat(deliveredItem.get("attemptCount").asInt()).isEqualTo(5);
        assertThat(deliveredItem.get("deliveredAt").asText()).isNotBlank();
        assertThat(deliveredItem.get("attempts")).hasSize(5);

        // 7. Replaying an already DELIVERED item must return 409 Conflict
        ResponseEntity<JsonNode> repeatReplayRes = restTemplate.exchange(
                "/api/v1/webhooks/dlq/" + deliveryId + "/replay",
                HttpMethod.POST,
                authEntity(analystJwt),
                JsonNode.class
        );
        assertThat(repeatReplayRes.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(repeatReplayRes.getBody().get("type").asText())
                .isEqualTo("https://ledgerpulse.dev/errors/invalid-delivery-state");
    }

    private String jwtFor(UserRole role, String email) {
        return jwtService.generateToken(new AuthenticatedOperator(
                UUID.randomUUID(),
                "Flow " + role.name(),
                email,
                role
        ));
    }

    private HttpEntity<Void> authEntity(String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Object> jsonEntity(String jwt, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
