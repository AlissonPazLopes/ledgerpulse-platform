package dev.ledgerpulse.core.refund;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerpulse.core.auth.domain.Merchant;
import dev.ledgerpulse.core.auth.domain.MerchantStatus;
import dev.ledgerpulse.core.auth.domain.UserAccount;
import dev.ledgerpulse.core.auth.repository.MerchantRepository;
import dev.ledgerpulse.core.auth.repository.UserAccountRepository;
import dev.ledgerpulse.core.auth.service.DevIdentitySeeder;
import dev.ledgerpulse.core.ledger.domain.Account;
import dev.ledgerpulse.core.ledger.domain.AccountStatus;
import dev.ledgerpulse.core.ledger.domain.AccountType;
import dev.ledgerpulse.core.ledger.domain.LedgerEntry;
import dev.ledgerpulse.core.ledger.repository.AccountRepository;
import dev.ledgerpulse.core.ledger.repository.LedgerEntryRepository;
import dev.ledgerpulse.core.outbox.OutboxEvent;
import dev.ledgerpulse.core.outbox.OutboxEventRepository;
import dev.ledgerpulse.core.payment.domain.PaymentStatus;
import dev.ledgerpulse.core.security.ApiKeyHasher;
import dev.ledgerpulse.core.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefundIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldExecutePartialAndFullCompensatingRefundsPreserveImmutabilityAndEnforceIdempotency() throws Exception {
        // Setup isolated merchant + MERCHANT + RECIPIENT accounts (0 initial balances)
        UUID merchantId = UUID.randomUUID();
        String merchantApiKey = "lp_refund_merchant_" + UUID.randomUUID();
        merchantRepository.saveAndFlush(new Merchant(
                merchantId,
                "Refund Isolated Merchant",
                ApiKeyHasher.sha256Hex(merchantApiKey),
                250,
                MerchantStatus.ACTIVE
        ));

        Account merchantAccount = accountRepository.saveAndFlush(new Account(
                UUID.randomUUID(),
                merchantId,
                AccountType.MERCHANT,
                "Refund Merchant Main",
                "BRL",
                AccountStatus.ACTIVE
        ));
        Account recipientAccount = accountRepository.saveAndFlush(new Account(
                UUID.randomUUID(),
                merchantId,
                AccountType.RECIPIENT,
                "Refund Split Partner",
                "BRL",
                AccountStatus.ACTIVE
        ));

        long escrowBefore = accountRepository.findById(DevIdentitySeeder.ESCROW_ACCOUNT_ID)
                .orElseThrow().getAvailableBalanceCents();
        long feeBefore = accountRepository.findById(DevIdentitySeeder.PLATFORM_FEE_ACCOUNT_ID)
                .orElseThrow().getAvailableBalanceCents();

        // 1. Settle a 15000-cent payment with 20% (2000 bps) split -> fee=375, netMerchant=11625, split=3000
        String sharedIdempotencyKey = "refund-shared-key-" + UUID.randomUUID();
        MvcResult paymentResult = mockMvc.perform(post("/api/v1/payments")
                        .header("X-API-Key", merchantApiKey)
                        .header("Idempotency-Key", sharedIdempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "payerId", "payer-refund-01",
                                "amountCents", 15_000L,
                                "currency", "BRL",
                                "paymentMethod", "CARD",
                                "description", "Payment for refund lifecycle test",
                                "splits", List.of(Map.of(
                                        "recipientAccountId", recipientAccount.getId().toString(),
                                        "splitType", "PERCENTAGE",
                                        "percentageBps", 2000
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andReturn();

        JsonNode paymentJson = objectMapper.readTree(paymentResult.getResponse().getContentAsString());
        UUID paymentId = UUID.fromString(paymentJson.get("id").asText());
        UUID settlementTxId = UUID.fromString(paymentJson.get("ledgerTransactionId").asText());

        List<LedgerEntry> originalEntriesSnapshot = ledgerEntryRepository
                .findByLedgerTransactionIdOrderByDirectionDescCreatedAtAscIdAsc(settlementTxId);
        assertThat(originalEntriesSnapshot).hasSize(4);

        // 2. VIEWER blocked (403); another merchant X-API-Key blocked (403)
        UserAccount viewer = userAccountRepository.findByEmailIgnoreCase("viewer@ledgerpulse.dev").orElseThrow();
        String viewerJwt = jwtService.generateToken(viewer);
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                        .header("Authorization", "Bearer " + viewerJwt)
                        .header("Idempotency-Key", "viewer-blocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\":5000,\"reason\":\"Viewer blocked test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/forbidden"));

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                        .header("X-API-Key", DevIdentitySeeder.DEV_MERCHANT_API_KEY)
                        .header("Idempotency-Key", "other-merchant-blocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\":5000,\"reason\":\"Other merchant blocked\"}"))
                .andExpect(status().isForbidden());

        // 3. Partial refund #1 (5000 cents) reusing the SAME Idempotency-Key from POST /payments -> proves scoped isolation
        String refund1Payload = objectMapper.writeValueAsString(Map.of(
                "amountCents", 5_000L,
                "reason", "Estorno parcial solicitado pelo comprador"
        ));
        MvcResult refund1Result = mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                        .header("X-API-Key", merchantApiKey)
                        .header("Idempotency-Key", sharedIdempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refund1Payload))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("X-Idempotent-Replay"))
                .andExpect(jsonPath("$.amountCents").value(5_000))
                .andExpect(jsonPath("$.platformFeeReversedCents").value(125))
                .andExpect(jsonPath("$.netMerchantReversedCents").value(3_875))
                .andExpect(jsonPath("$.recipientReversals[0].recipientAccountId").value(recipientAccount.getId().toString()))
                .andExpect(jsonPath("$.recipientReversals[0].amountCents").value(1_000))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.totalRefundedCents").value(5_000))
                .andExpect(jsonPath("$.remainingRefundableCents").value(10_000))
                .andExpect(jsonPath("$.idempotencyKey").value(sharedIdempotencyKey))
                .andReturn();

        UUID refund1TxId = UUID.fromString(
                objectMapper.readTree(refund1Result.getResponse().getContentAsString())
                        .get("ledgerTransactionId").asText()
        );

        // 4. Idempotent replay with same key & same payload -> 201 + X-Idempotent-Replay: true
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                        .header("X-API-Key", merchantApiKey)
                        .header("Idempotency-Key", sharedIdempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refund1Payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.ledgerTransactionId").value(refund1TxId.toString()));

        // 5. Divergent payload with same key -> 409 idempotency-conflict
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                        .header("X-API-Key", merchantApiKey)
                        .header("Idempotency-Key", sharedIdempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\":6000,\"reason\":\"Payload divergente\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/idempotency-conflict"));

        // 6. Exceeding remaining refundable balance (10001 > 10000) -> 400 validation-error
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                        .header("X-API-Key", merchantApiKey)
                        .header("Idempotency-Key", "refund-too-large-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\":10001,\"reason\":\"Valor excedente\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/validation-error"));

        // 7. Final refund omitting amountCents (null) as ANALYST -> refunds remaining 10000 -> REFUNDED
        UserAccount analyst = userAccountRepository.findByEmailIgnoreCase("analyst@ledgerpulse.dev").orElseThrow();
        String analystJwt = jwtService.generateToken(analyst);
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                        .header("Authorization", "Bearer " + analystJwt)
                        .header("Idempotency-Key", "refund-final-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Estorno complementar final\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountCents").value(10_000))
                .andExpect(jsonPath("$.platformFeeReversedCents").value(250))
                .andExpect(jsonPath("$.netMerchantReversedCents").value(7_750))
                .andExpect(jsonPath("$.recipientReversals[0].amountCents").value(2_000))
                .andExpect(jsonPath("$.paymentStatus").value(PaymentStatus.REFUNDED.name()))
                .andExpect(jsonPath("$.totalRefundedCents").value(15_000))
                .andExpect(jsonPath("$.remainingRefundableCents").value(0));

        // 8. Verify balances restored & original settlement ledger_entries completely immutable
        assertThat(accountRepository.findById(merchantAccount.getId()).orElseThrow().getAvailableBalanceCents()).isZero();
        assertThat(accountRepository.findById(recipientAccount.getId()).orElseThrow().getAvailableBalanceCents()).isZero();
        assertThat(accountRepository.findById(DevIdentitySeeder.ESCROW_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents())
                .isEqualTo(escrowBefore);
        assertThat(accountRepository.findById(DevIdentitySeeder.PLATFORM_FEE_ACCOUNT_ID).orElseThrow().getAvailableBalanceCents())
                .isEqualTo(feeBefore);

        List<LedgerEntry> currentOriginalEntries = ledgerEntryRepository
                .findByLedgerTransactionIdOrderByDirectionDescCreatedAtAscIdAsc(settlementTxId);
        assertThat(currentOriginalEntries)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(originalEntriesSnapshot);

        List<OutboxEvent> paymentOutbox = outboxEventRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("PAYMENT", paymentId);
        assertThat(paymentOutbox.stream().filter(e -> "payment.refunded".equals(e.getEventType())).count())
                .isEqualTo(2L);

        // 9. Further refund on REFUNDED payment -> 409 invalid-payment-state
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                        .header("Authorization", "Bearer " + analystJwt)
                        .header("Idempotency-Key", "refund-after-complete-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\":100,\"reason\":\"Tentativa apos total\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/invalid-payment-state"));

        // 10. GET /api/v1/payments/{id}/refunds returns 2 items ordered by createdAt DESC
        mockMvc.perform(get("/api/v1/payments/" + paymentId + "/refunds")
                        .header("Authorization", "Bearer " + viewerJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].amountCents").value(10_000))
                .andExpect(jsonPath("$.items[1].amountCents").value(5_000));
    }
}
