package dev.ledgerpulse.core.payment;

import com.jayway.jsonpath.JsonPath;
import dev.ledgerpulse.core.auth.domain.Merchant;
import dev.ledgerpulse.core.auth.domain.MerchantStatus;
import dev.ledgerpulse.core.auth.repository.MerchantRepository;
import dev.ledgerpulse.core.auth.service.DevIdentitySeeder;
import dev.ledgerpulse.core.ledger.repository.AccountRepository;
import dev.ledgerpulse.core.outbox.OutboxEvent;
import dev.ledgerpulse.core.outbox.OutboxEventRepository;
import dev.ledgerpulse.core.outbox.OutboxStatus;
import dev.ledgerpulse.core.payment.repository.PaymentRepository;
import dev.ledgerpulse.core.security.ApiKeyHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Test
    void shouldCreateSettledPaymentWithPercentageSplitLedgerAndOutboxAndSupportIdempotentReplayAndConflict() throws Exception {
        long escrowBefore = accountRepository.findById(DevIdentitySeeder.ESCROW_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents();
        long feeBefore = accountRepository.findById(DevIdentitySeeder.PLATFORM_FEE_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents();
        long merchantBefore = accountRepository.findById(DevIdentitySeeder.DEMO_MERCHANT_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents();
        long recipientBefore = accountRepository.findById(DevIdentitySeeder.DEMO_RECIPIENT_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents();

        String idemKey = "idem-pay-" + UUID.randomUUID();
        String requestBody = """
                {
                  "payerId": "payer-98765",
                  "amountCents": 15000,
                  "currency": "BRL",
                  "paymentMethod": "CARD",
                  "description": "Pedido #4021",
                  "splits": [
                    {
                      "recipientAccountId": "22222222-2222-4222-8222-222222222222",
                      "splitType": "PERCENTAGE",
                      "percentageBps": 2000
                    }
                  ]
                }
                """;

        String createdResponse = mockMvc.perform(post("/api/v1/payments")
                        .header("X-API-Key", DevIdentitySeeder.DEV_MERCHANT_API_KEY)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("X-Idempotent-Replay"))
                .andExpect(jsonPath("$.id", not(blankOrNullString())))
                .andExpect(jsonPath("$.merchantId").value(DevIdentitySeeder.DEMO_MERCHANT_ID.toString()))
                .andExpect(jsonPath("$.amountCents").value(15000))
                .andExpect(jsonPath("$.platformFeeCents").value(375))
                .andExpect(jsonPath("$.netMerchantCents").value(11625))
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.riskScore").value(0))
                .andExpect(jsonPath("$.riskDecision").value("APPROVE"))
                .andExpect(jsonPath("$.ledgerTransactionId", not(blankOrNullString())))
                .andExpect(jsonPath("$.splits", hasSize(1)))
                .andExpect(jsonPath("$.splits[0].amountCents").value(3000))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID paymentId = UUID.fromString(JsonPath.read(createdResponse, "$.id"));
        String ledgerTxId = JsonPath.read(createdResponse, "$.ledgerTransactionId");

        // Account balances updated accurately
        assertThat(accountRepository.findById(DevIdentitySeeder.ESCROW_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents())
                .isEqualTo(escrowBefore - 15_000L);
        assertThat(accountRepository.findById(DevIdentitySeeder.PLATFORM_FEE_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents())
                .isEqualTo(feeBefore + 375L);
        assertThat(accountRepository.findById(DevIdentitySeeder.DEMO_MERCHANT_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents())
                .isEqualTo(merchantBefore + 11_625L);
        assertThat(accountRepository.findById(DevIdentitySeeder.DEMO_RECIPIENT_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents())
                .isEqualTo(recipientBefore + 3_000L);

        // Outbox event persisted atomically in PENDING state
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("PAYMENT", paymentId);
        assertThat(outboxEvents).hasSize(1);
        OutboxEvent outbox = outboxEvents.get(0);
        assertThat(outbox.getEventType()).isEqualTo("payment.settled");
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getPayloadJson()).contains("\"eventType\":\"payment.settled\"");
        assertThat(outbox.getPayloadJson()).contains("\"amountCents\":15000");

        // Balanced LedgerTransaction exposed via GET /api/v1/ledger/transactions/{id}
        String adminToken = login("admin@ledgerpulse.dev");
        mockMvc.perform(get("/api/v1/ledger/transactions/" + ledgerTxId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true))
                .andExpect(jsonPath("$.totalDebitCents").value(15000))
                .andExpect(jsonPath("$.totalCreditCents").value(15000))
                .andExpect(jsonPath("$.entries", hasSize(4)));

        // Identical replay returns cached response with X-Idempotent-Replay: true and no duplicate side effects
        long paymentCountBeforeReplay = paymentRepository.count();
        mockMvc.perform(post("/api/v1/payments")
                        .header("X-API-Key", DevIdentitySeeder.DEV_MERCHANT_API_KEY)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(paymentId.toString()));

        assertThat(paymentRepository.count()).isEqualTo(paymentCountBeforeReplay);

        // Divergent payload with same Idempotency-Key -> 409 idempotency-conflict
        mockMvc.perform(post("/api/v1/payments")
                        .header("X-API-Key", DevIdentitySeeder.DEV_MERCHANT_API_KEY)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payerId": "payer-98765",
                                  "amountCents": 25000,
                                  "currency": "BRL",
                                  "paymentMethod": "CARD",
                                  "description": "Pedido #4021",
                                  "splits": []
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/idempotency-conflict"))
                .andExpect(jsonPath("$.status").value(409));

        // GET /api/v1/payments/{id} works for owning merchant and JWT operator, 403 for another merchant
        mockMvc.perform(get("/api/v1/payments/" + paymentId)
                        .header("X-API-Key", DevIdentitySeeder.DEV_MERCHANT_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()));

        String otherApiKey = "lp_other_merchant_" + UUID.randomUUID();
        merchantRepository.saveAndFlush(new Merchant(
                UUID.randomUUID(),
                "Other Merchant",
                ApiKeyHasher.sha256Hex(otherApiKey),
                250,
                MerchantStatus.ACTIVE
        ));

        mockMvc.perform(get("/api/v1/payments/" + paymentId)
                        .header("X-API-Key", otherApiKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/forbidden"));
    }

    @Test
    void shouldCreateFixedSplitPaymentViaJwtAndListPaymentsAndRejectMissingIdempotencyKey() throws Exception {
        String analystToken = login("analyst@ledgerpulse.dev");

        // Missing Idempotency-Key -> 400 validation-error
        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
                                  "payerId": "payer-no-idem",
                                  "amountCents": 10000,
                                  "currency": "BRL",
                                  "paymentMethod": "PIX"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/validation-error"));

        String idemKey = "idem-jwt-" + UUID.randomUUID();
        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
                                  "payerId": "payer-pix-1",
                                  "amountCents": 10000,
                                  "currency": "BRL",
                                  "paymentMethod": "PIX",
                                  "description": "Pagamento PIX Split Fixo",
                                  "splits": [
                                    {
                                      "recipientAccountId": "22222222-2222-4222-8222-222222222222",
                                      "splitType": "FIXED",
                                      "amountCents": 2000
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.platformFeeCents").value(250))
                .andExpect(jsonPath("$.netMerchantCents").value(7750))
                .andExpect(jsonPath("$.splits[0].amountCents").value(2000))
                .andExpect(jsonPath("$.status").value("SETTLED"));

        mockMvc.perform(get("/api/v1/payments")
                        .param("status", "SETTLED")
                        .param("merchantId", DevIdentitySeeder.DEMO_MERCHANT_ID.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("SETTLED"));
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "LedgerPulse@2026"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }
}
