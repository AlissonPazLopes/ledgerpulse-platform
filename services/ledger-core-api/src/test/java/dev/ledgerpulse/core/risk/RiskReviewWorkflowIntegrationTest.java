package dev.ledgerpulse.core.risk;

import com.jayway.jsonpath.JsonPath;
import dev.ledgerpulse.core.auth.service.DevIdentitySeeder;
import dev.ledgerpulse.core.ledger.repository.AccountRepository;
import dev.ledgerpulse.core.outbox.OutboxEvent;
import dev.ledgerpulse.core.outbox.OutboxEventRepository;
import dev.ledgerpulse.core.risk.review.RiskReviewDecisionRepository;
import dev.ledgerpulse.core.risk.review.RiskReviewDecisionType;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RiskReviewWorkflowIntegrationTest {

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
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private RiskReviewDecisionRepository decisionRepository;

    @Test
    void shouldEnforceRbacAndExecuteManualReviewApproveAndDeclineTransitions() throws Exception {
        String viewerToken = login("viewer@ledgerpulse.dev");
        String analystToken = login("analyst@ledgerpulse.dev");

        // 1. Create two MANUAL_REVIEW payments (one with split, one without)
        enqueueReviewResponse(58, "[\"NIGHT_ANOMALY\",\"ML_ANOMALY_DETECTED\"]");
        UUID paymentToApprove = createReviewPaymentWithSplit();

        enqueueReviewResponse(64, "[\"HIGH_TICKET_NEW_ACCOUNT\"]");
        UUID paymentToDecline = createReviewPaymentWithoutSplit();

        // 2. RBAC: VIEWER is forbidden (403) on both GET and POST decision
        mockMvc.perform(get("/api/v1/risk/reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/forbidden"));

        mockMvc.perform(post("/api/v1/risk/reviews/" + paymentToApprove + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "justification": "Viewer attempt must fail"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/forbidden"));

        // 3. ANALYST lists pending reviews
        mockMvc.perform(get("/api/v1/risk/reviews")
                        .param("minRiskScore", "55")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].paymentId", hasItem(paymentToApprove.toString())))
                .andExpect(jsonPath("$.items[*].paymentId", hasItem(paymentToDecline.toString())))
                .andExpect(jsonPath("$.items[0].status").value("MANUAL_REVIEW"));

        // 4. Validation error on short justification
        mockMvc.perform(post("/api/v1/risk/reviews/" + paymentToApprove + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "justification": "ok"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/validation-error"));

        // 5. ANALYST approves payment -> settles double-entry ledger + split + emits payment.settled & risk.feedback.recorded
        long escrowBefore = accountRepository.findById(DevIdentitySeeder.ESCROW_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents();
        long feeBefore = accountRepository.findById(DevIdentitySeeder.PLATFORM_FEE_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents();
        long merchantBefore = accountRepository.findById(DevIdentitySeeder.DEMO_MERCHANT_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents();
        long recipientBefore = accountRepository.findById(DevIdentitySeeder.DEMO_RECIPIENT_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents();

        String approveBody = mockMvc.perform(post("/api/v1/risk/reviews/" + paymentToApprove + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "justification": "Titular confirmou a operacao via contato telefonico antifraude."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionId", not(blankOrNullString())))
                .andExpect(jsonPath("$.paymentId").value(paymentToApprove.toString()))
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.paymentStatus").value("SETTLED"))
                .andExpect(jsonPath("$.ledgerTransactionId", not(blankOrNullString())))
                .andExpect(jsonPath("$.reviewedByEmail").value("analyst@ledgerpulse.dev"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(accountRepository.findById(DevIdentitySeeder.ESCROW_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents())
                .isEqualTo(escrowBefore - 15_000L);
        assertThat(accountRepository.findById(DevIdentitySeeder.PLATFORM_FEE_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents())
                .isEqualTo(feeBefore + 375L);
        assertThat(accountRepository.findById(DevIdentitySeeder.DEMO_MERCHANT_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents())
                .isEqualTo(merchantBefore + 11_625L);
        assertThat(accountRepository.findById(DevIdentitySeeder.DEMO_RECIPIENT_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents())
                .isEqualTo(recipientBefore + 3_000L);

        List<OutboxEvent> approvedOutbox = outboxEventRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("PAYMENT", paymentToApprove);
        assertThat(approvedOutbox).extracting(OutboxEvent::getEventType)
                .containsExactly("payment.manual_review_required", "payment.settled", "risk.feedback.recorded");

        var savedDecision = decisionRepository.findByPaymentId(paymentToApprove).orElseThrow();
        assertThat(savedDecision.getDecision()).isEqualTo(RiskReviewDecisionType.APPROVE);
        assertThat(UUID.fromString(JsonPath.read(approveBody, "$.decisionId"))).isEqualTo(savedDecision.getId());

        // 6. Attempting to decide again on SETTLED payment -> 409 invalid-payment-state
        mockMvc.perform(post("/api/v1/risk/reviews/" + paymentToApprove + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "DECLINE",
                                  "justification": "Segunda decisao nao pode ser aceita."
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/invalid-payment-state"))
                .andExpect(jsonPath("$.status").value(409));

        // 7. ANALYST declines second payment -> DECLINED, balances unchanged, outbox payment.declined + risk.feedback.recorded
        long escrowAfterApprove = accountRepository.findById(DevIdentitySeeder.ESCROW_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents();
        long merchantAfterApprove = accountRepository.findById(DevIdentitySeeder.DEMO_MERCHANT_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents();

        mockMvc.perform(post("/api/v1/risk/reviews/" + paymentToDecline + "/decision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "DECLINE",
                                  "justification": "Pagador nao reconheceu a transacao na validacao."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DECLINE"))
                .andExpect(jsonPath("$.paymentStatus").value("DECLINED"))
                .andExpect(jsonPath("$.ledgerTransactionId").value((Object) null));

        assertThat(accountRepository.findById(DevIdentitySeeder.ESCROW_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents())
                .isEqualTo(escrowAfterApprove);
        assertThat(accountRepository.findById(DevIdentitySeeder.DEMO_MERCHANT_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents())
                .isEqualTo(merchantAfterApprove);

        List<OutboxEvent> declinedOutbox = outboxEventRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("PAYMENT", paymentToDecline);
        assertThat(declinedOutbox).extracting(OutboxEvent::getEventType)
                .containsExactly("payment.manual_review_required", "payment.declined", "risk.feedback.recorded");
    }

    private void enqueueReviewResponse(int score, String reasonCodesJson) {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "risk_score": %d,
                          "decision": "REVIEW",
                          "reason_codes": %s,
                          "model_version": "v1.0.0"
                        }
                        """.formatted(score, reasonCodesJson)));
    }

    private UUID createReviewPaymentWithSplit() throws Exception {
        String body = mockMvc.perform(post("/api/v1/payments")
                        .header("X-API-Key", DevIdentitySeeder.DEV_MERCHANT_API_KEY)
                        .header("Idempotency-Key", "idem-rev-approve-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payerId": "payer-review-1",
                                  "amountCents": 15000,
                                  "currency": "BRL",
                                  "paymentMethod": "CARD",
                                  "description": "Compra em revisao com split",
                                  "splits": [
                                    {
                                      "recipientAccountId": "22222222-2222-4222-8222-222222222222",
                                      "splitType": "PERCENTAGE",
                                      "percentageBps": 2000
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MANUAL_REVIEW"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID createReviewPaymentWithoutSplit() throws Exception {
        String body = mockMvc.perform(post("/api/v1/payments")
                        .header("X-API-Key", DevIdentitySeeder.DEV_MERCHANT_API_KEY)
                        .header("Idempotency-Key", "idem-rev-decline-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payerId": "payer-review-2",
                                  "amountCents": 40000,
                                  "currency": "BRL",
                                  "paymentMethod": "CARD",
                                  "description": "Compra suspeita para declinar"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MANUAL_REVIEW"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
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
