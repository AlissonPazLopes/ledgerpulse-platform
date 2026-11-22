package dev.ledgerpulse.core.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerpulse.core.auth.service.DevIdentitySeeder;
import dev.ledgerpulse.core.ledger.repository.AccountRepository;
import dev.ledgerpulse.core.outbox.OutboxEvent;
import dev.ledgerpulse.core.outbox.OutboxEventRepository;
import dev.ledgerpulse.core.outbox.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class LedgerCoreTestcontainersIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ledgerpulse")
            .withUsername("ledgerpulse")
            .withPassword("ledgerpulse_dev");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.idempotency.store", () -> "redis");
        registry.add("management.health.redis.enabled", () -> "true");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void shouldPersistDoubleEntryOutboxInPostgres16AndHandleRedis7IdempotencyEndToEnd() throws Exception {
        String idempotencyKey = "tc-idem-" + UUID.randomUUID();
        String payload = """
                {
                  "payerId": "tc-payer-01",
                  "amountCents": 15000,
                  "currency": "BRL",
                  "paymentMethod": "CARD",
                  "description": "Testcontainers Payment",
                  "splits": [
                    {
                      "recipientAccountId": "22222222-2222-4222-8222-222222222222",
                      "splitType": "PERCENTAGE",
                      "percentageBps": 2000
                    }
                  ]
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", DevIdentitySeeder.DEV_MERCHANT_API_KEY);
        headers.set("Idempotency-Key", idempotencyKey);

        ResponseEntity<String> createResp = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>(payload, headers),
                String.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResp.getHeaders().getFirst("X-Idempotent-Replay")).isNull();

        JsonNode createdJson = objectMapper.readTree(createResp.getBody());
        UUID paymentId = UUID.fromString(createdJson.get("id").asText());
        assertThat(createdJson.get("status").asText()).isEqualTo("SETTLED");
        assertThat(createdJson.get("platformFeeCents").asLong()).isEqualTo(375L);
        assertThat(createdJson.get("netMerchantCents").asLong()).isEqualTo(11_625L);

        assertThat(accountRepository.findById(DevIdentitySeeder.ESCROW_ACCOUNT_ID)
                .orElseThrow().getAvailableBalanceCents()).isEqualTo(-15_000L);
        assertThat(accountRepository.findById(DevIdentitySeeder.PLATFORM_FEE_ACCOUNT_ID)
                .orElseThrow().getAvailableBalanceCents()).isEqualTo(375L);
        assertThat(accountRepository.findById(DevIdentitySeeder.DEMO_MERCHANT_ACCOUNT_ID)
                .orElseThrow().getAvailableBalanceCents()).isEqualTo(11_625L);
        assertThat(accountRepository.findById(DevIdentitySeeder.DEMO_RECIPIENT_ACCOUNT_ID)
                .orElseThrow().getAvailableBalanceCents()).isEqualTo(3_000L);

        List<OutboxEvent> outboxEvents = outboxEventRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("PAYMENT", paymentId);
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("payment.settled");

        String redisKey = "ledgerpulse:idempotency:" + DevIdentitySeeder.DEMO_MERCHANT_ID + ":" + idempotencyKey;
        assertThat(stringRedisTemplate.hasKey(redisKey)).isTrue();

        ResponseEntity<String> replayResp = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>(payload, headers),
                String.class
        );
        assertThat(replayResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replayResp.getHeaders().getFirst("X-Idempotent-Replay")).isEqualTo("true");
        JsonNode replayJson = objectMapper.readTree(replayResp.getBody());
        assertThat(replayJson.get("id").asText()).isEqualTo(paymentId.toString());

        String divergentPayload = """
                {
                  "payerId": "tc-payer-01",
                  "amountCents": 99999,
                  "currency": "BRL",
                  "paymentMethod": "CARD",
                  "description": "Divergent amount",
                  "splits": []
                }
                """;
        ResponseEntity<String> conflictResp = restTemplate.postForEntity(
                "/api/v1/payments",
                new HttpEntity<>(divergentPayload, headers),
                String.class
        );
        assertThat(conflictResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode conflictJson = objectMapper.readTree(conflictResp.getBody());
        assertThat(conflictJson.get("type").asText())
                .isEqualTo("https://ledgerpulse.dev/errors/idempotency-conflict");
    }
}
