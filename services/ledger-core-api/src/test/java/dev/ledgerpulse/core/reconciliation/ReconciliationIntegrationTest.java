package dev.ledgerpulse.core.reconciliation;

import dev.ledgerpulse.core.auth.domain.UserAccount;
import dev.ledgerpulse.core.auth.repository.UserAccountRepository;
import dev.ledgerpulse.core.auth.service.DevIdentitySeeder;
import dev.ledgerpulse.core.ledger.domain.Account;
import dev.ledgerpulse.core.ledger.repository.AccountRepository;
import dev.ledgerpulse.core.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ledgerpulse_reconciliation_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS ledger_core"
})
class ReconciliationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRunBalancedAndDivergentReconciliationEnforceRbacPaginateAndExportCsv() throws Exception {
        UserAccount admin = userAccountRepository.findByEmailIgnoreCase("admin@ledgerpulse.dev").orElseThrow();
        UserAccount analyst = userAccountRepository.findByEmailIgnoreCase("analyst@ledgerpulse.dev").orElseThrow();
        UserAccount viewer = userAccountRepository.findByEmailIgnoreCase("viewer@ledgerpulse.dev").orElseThrow();
        String adminJwt = jwtService.generateToken(admin);
        String analystJwt = jwtService.generateToken(analyst);
        String viewerJwt = jwtService.generateToken(viewer);

        // 1. ANALYST & VIEWER blocked from manual run (ADMIN only) -> 403
        mockMvc.perform(post("/api/v1/reconciliation/run")
                        .header("Authorization", "Bearer " + analystJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/reconciliation/run")
                        .header("Authorization", "Bearer " + viewerJwt))
                .andExpect(status().isForbidden());

        // 2. Execute real payment so accounts & ledger have activity
        String paymentBody = mockMvc.perform(post("/api/v1/payments")
                        .header("X-API-Key", DevIdentitySeeder.DEV_MERCHANT_API_KEY)
                        .header("Idempotency-Key", "recon-pay-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payerId": "payer-recon-01",
                                  "amountCents": 10000,
                                  "currency": "BRL",
                                  "paymentMethod": "PIX",
                                  "description": "Reconciliation test payment"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(paymentBody).contains("SETTLED");

        // 3. Run balanced reconciliation as ADMIN -> 201 BALANCED
        mockMvc.perform(post("/api/v1/reconciliation/run")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BALANCED"))
                .andExpect(jsonPath("$.divergentAccountsCount").value(0))
                .andExpect(jsonPath("$.unbalancedTransactionsCount").value(0))
                .andExpect(jsonPath("$.totalSettledPaymentsCents").value(10_000))
                .andExpect(jsonPath("$.totalLedgerSettlementsCents").value(10_000))
                .andExpect(jsonPath("$.discrepancyCents").value(0))
                .andExpect(jsonPath("$.triggerSource").value("MANUAL"))
                .andExpect(jsonPath("$.findings").isEmpty());

        // 4. Induce balance divergence directly in ledger_core.accounts via SQL -> expect DIVERGENT
        Account merchantAccount = accountRepository.findById(DevIdentitySeeder.DEMO_MERCHANT_ACCOUNT_ID).orElseThrow();
        long originalBalance = merchantAccount.getAvailableBalanceCents();
        try {
            jdbcTemplate.update(
                    "UPDATE ledger_core.accounts SET available_balance_cents = available_balance_cents + 500 WHERE id = ?",
                    merchantAccount.getId()
            );

            mockMvc.perform(post("/api/v1/reconciliation/run")
                            .header("Authorization", "Bearer " + adminJwt))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("DIVERGENT"))
                    .andExpect(jsonPath("$.divergentAccountsCount").value(1))
                    .andExpect(jsonPath("$.discrepancyCents").value(500))
                    .andExpect(jsonPath("$.findings[0]").exists());
        } finally {
            jdbcTemplate.update(
                    "UPDATE ledger_core.accounts SET available_balance_cents = ? WHERE id = ?",
                    originalBalance,
                    merchantAccount.getId()
            );
        }

        // 5. List reports paginated (JWT VIEWER allowed)
        mockMvc.perform(get("/api/v1/reconciliation/reports")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + viewerJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].status").value("DIVERGENT"))
                .andExpect(jsonPath("$.items[1].status").value("BALANCED"));

        // 6. Export CSV with exact 13-column header
        String csv = mockMvc.perform(get("/api/v1/reconciliation/export.csv")
                        .header("Authorization", "Bearer " + viewerJwt))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"ledgerpulse-reconciliation.csv\""))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String[] lines = csv.trim().split("\\R");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo(
                "reportId,createdAt,status,triggerSource,checkedAccountsCount,divergentAccountsCount,"
                        + "checkedTransactionsCount,unbalancedTransactionsCount,totalSettledPaymentsCents,"
                        + "totalLedgerSettlementsCents,totalRefundsCents,totalLedgerRefundsCents,discrepancyCents"
        );
        assertThat(lines[1]).contains(",DIVERGENT,MANUAL,");
        assertThat(lines[2]).contains(",BALANCED,MANUAL,");
    }
}
