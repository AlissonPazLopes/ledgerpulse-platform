package dev.ledgerpulse.core.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerpulse.core.auth.domain.UserAccount;
import dev.ledgerpulse.core.auth.repository.UserAccountRepository;
import dev.ledgerpulse.core.auth.service.DevIdentitySeeder;
import dev.ledgerpulse.core.security.JwtService;
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SseStreamIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRequireJwtForSseStreamStreamLivePaymentSettledEventAndServeDashboardSummary() throws Exception {
        // 1. Unauthenticated SSE stream must return 401
        ResponseEntity<String> unauthResponse = restTemplate.getForEntity("/api/v1/stream/events", String.class);
        assertThat(unauthResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 2. X-API-Key cannot open operator SSE stream (403)
        HttpHeaders apiKeyHeaders = new HttpHeaders();
        apiKeyHeaders.set("X-API-Key", DevIdentitySeeder.DEV_MERCHANT_API_KEY);
        ResponseEntity<String> apiKeyStreamRes = restTemplate.exchange(
                "/api/v1/stream/events",
                HttpMethod.GET,
                new HttpEntity<>(apiKeyHeaders),
                String.class
        );
        assertThat(apiKeyStreamRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 3. Open live SSE stream with JWT
        UserAccount admin = userAccountRepository.findByEmailIgnoreCase("admin@ledgerpulse.dev").orElseThrow();
        String jwt = jwtService.generateToken(admin);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest sseRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/stream/events"))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .GET()
                .build();

        HttpResponse<InputStream> sseResponse = client.send(sseRequest, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(sseResponse.statusCode()).isEqualTo(200);
        assertThat(sseResponse.headers().firstValue("Content-Type").orElse("")).contains(MediaType.TEXT_EVENT_STREAM_VALUE);

        try (InputStream bodyStream = sseResponse.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {

            // 4. Read initial "connected" event
            SseFrame connectedFrame = readNextEvent(reader, 5);
            assertThat(connectedFrame.eventName()).isEqualTo("connected");
            JsonNode connectedJson = objectMapper.readTree(connectedFrame.data());
            assertThat(connectedJson.get("eventType").asText()).isEqualTo("connected");
            assertThat(connectedJson.get("message").asText()).isEqualTo("SSE stream connected");

            // 5. Create settled payment and verify live "payment.settled" arrives via AFTER_COMMIT SSE broadcast
            HttpHeaders paymentHeaders = new HttpHeaders();
            paymentHeaders.set("X-API-Key", DevIdentitySeeder.DEV_MERCHANT_API_KEY);
            paymentHeaders.set("Idempotency-Key", "sse-payment-" + UUID.randomUUID());
            paymentHeaders.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<JsonNode> paymentRes = restTemplate.exchange(
                    "/api/v1/payments",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of(
                            "payerId", "payer-sse-live-01",
                            "amountCents", 15_000L,
                            "currency", "BRL",
                            "paymentMethod", "PIX",
                            "description", "SSE Real-Time Test Payment"
                    ), paymentHeaders),
                    JsonNode.class
            );
            assertThat(paymentRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String paymentId = paymentRes.getBody().get("id").asText();

            SseFrame settledFrame = readNextEvent(reader, 5);
            assertThat(settledFrame.eventName()).isEqualTo("payment.settled");
            JsonNode settledJson = objectMapper.readTree(settledFrame.data());
            assertThat(settledJson.get("eventType").asText()).isEqualTo("payment.settled");
            assertThat(settledJson.get("payload").get("paymentId").asText()).isEqualTo(paymentId);
            assertThat(settledJson.get("payload").get("amountCents").asLong()).isEqualTo(15_000L);
            assertThat(settledJson.get("payload").get("status").asText()).isEqualTo("SETTLED");
        }

        // 6. Query GET /api/v1/dashboard/summary with JWT
        HttpHeaders jwtHeaders = new HttpHeaders();
        jwtHeaders.setBearerAuth(jwt);
        ResponseEntity<JsonNode> summaryRes = restTemplate.exchange(
                "/api/v1/dashboard/summary",
                HttpMethod.GET,
                new HttpEntity<>(jwtHeaders),
                JsonNode.class
        );
        assertThat(summaryRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode summary = summaryRes.getBody();
        assertThat(summary.get("tpvCents").asLong()).isGreaterThanOrEqualTo(15_000L);
        assertThat(summary.get("settledCount").asLong()).isGreaterThanOrEqualTo(1L);
        assertThat(summary.get("approvalRateBps").asInt()).isBetween(0, 10_000);
        assertThat(summary.get("averageRiskScore").asInt()).isBetween(0, 100);
        assertThat(summary.get("hourlySeries").isArray()).isTrue();
        assertThat(summary.get("hourlySeries")).isNotEmpty();
    }

    private SseFrame readNextEvent(BufferedReader reader, int timeoutSeconds) throws Exception {
        CompletableFuture<SseFrame> future = CompletableFuture.supplyAsync(() -> {
            try {
                String eventName = null;
                StringBuilder data = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (eventName != null || !data.isEmpty()) {
                            return new SseFrame(eventName, data.toString());
                        }
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        eventName = line.substring("event:".length()).trim();
                    } else if (line.startsWith("data:")) {
                        if (!data.isEmpty()) {
                            data.append('\n');
                        }
                        data.append(line.substring("data:".length()).trim());
                    }
                }
                throw new IllegalStateException("SSE stream closed before full event received");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
    }

    private record SseFrame(String eventName, String data) {
    }
}
