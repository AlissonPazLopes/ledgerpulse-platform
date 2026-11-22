package dev.ledgerpulse.core.ledger;

import com.jayway.jsonpath.JsonPath;
import dev.ledgerpulse.core.auth.service.DevIdentitySeeder;
import dev.ledgerpulse.core.ledger.domain.AccountType;
import dev.ledgerpulse.core.ledger.domain.EntryDirection;
import dev.ledgerpulse.core.ledger.domain.LedgerReferenceType;
import dev.ledgerpulse.core.ledger.domain.LedgerTransaction;
import dev.ledgerpulse.core.ledger.service.LedgerPostingCommand;
import dev.ledgerpulse.core.ledger.service.LedgerService;
import dev.ledgerpulse.core.web.GlobalExceptionHandler;
import dev.ledgerpulse.core.web.ProblemDetailFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerAndAccountsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private ProblemDetailFactory problemDetailFactory;

    @Test
    void shouldCreateListAndFetchAccountsWithRbacAndValidation() throws Exception {
        String adminToken = login("admin@ledgerpulse.dev");
        String viewerToken = login("viewer@ledgerpulse.dev");

        // VIEWER cannot create accounts -> 403
        mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
                                  "accountType": "RECIPIENT",
                                  "holderName": "Blocked Viewer Attempt",
                                  "currency": "BRL"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/forbidden"));

        // Non-existent merchantId -> 404 resource-not-found
        mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": "99999999-9999-4999-8999-999999999999",
                                  "accountType": "RECIPIENT",
                                  "holderName": "Unknown Merchant",
                                  "currency": "BRL"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/resource-not-found"));

        // ADMIN creates new RECIPIENT account -> 201
        String createdBody = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": "7b92c3e0-1f42-4876-a1e2-901283471234",
                                  "accountType": "RECIPIENT",
                                  "holderName": "Partner Seller Beta",
                                  "currency": "BRL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountType").value("RECIPIENT"))
                .andExpect(jsonPath("$.holderName").value("Partner Seller Beta"))
                .andExpect(jsonPath("$.availableBalanceCents").value(0))
                .andExpect(jsonPath("$.pendingBalanceCents").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String createdId = JsonPath.read(createdBody, "$.id");

        mockMvc.perform(get("/api/v1/accounts/" + createdId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId))
                .andExpect(jsonPath("$.holderName").value("Partner Seller Beta"));

        mockMvc.perform(get("/api/v1/accounts")
                        .param("accountType", "RECIPIENT")
                        .param("merchantId", DevIdentitySeeder.DEMO_MERCHANT_ID.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/api/v1/accounts/99999999-9999-4999-8999-999999999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/resource-not-found"));
    }

    @Test
    void shouldListAndDetailBalancedLedgerTransactions() throws Exception {
        String viewerToken = login("viewer@ledgerpulse.dev");
        UUID refId = UUID.randomUUID();

        LedgerTransaction tx = ledgerService.postTransaction(new LedgerPostingCommand(
                LedgerReferenceType.MANUAL_ADJUSTMENT,
                refId,
                "Audit ledger test tx",
                "BRL",
                "corr-audit-1",
                List.of(
                        new LedgerPostingCommand.LedgerLeg(DevIdentitySeeder.ESCROW_ACCOUNT_ID, EntryDirection.DEBIT, 5_000L),
                        new LedgerPostingCommand.LedgerLeg(DevIdentitySeeder.DEMO_MERCHANT_ACCOUNT_ID, EntryDirection.CREDIT, 4_500L),
                        new LedgerPostingCommand.LedgerLeg(DevIdentitySeeder.PLATFORM_FEE_ACCOUNT_ID, EntryDirection.CREDIT, 500L)
                )
        ));

        mockMvc.perform(get("/api/v1/ledger/transactions")
                        .param("referenceId", refId.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(tx.getId().toString()))
                .andExpect(jsonPath("$.items[0].totalDebitCents").value(5000))
                .andExpect(jsonPath("$.items[0].totalCreditCents").value(5000))
                .andExpect(jsonPath("$.items[0].balanced").value(true));

        mockMvc.perform(get("/api/v1/ledger/transactions/" + tx.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tx.getId().toString()))
                .andExpect(jsonPath("$.balanced").value(true))
                .andExpect(jsonPath("$.entries", hasSize(3)))
                .andExpect(jsonPath("$.entries[0].accountType").value(AccountType.ESCROW.name()))
                .andExpect(jsonPath("$.entries[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].amountCents").value(5000));
    }

    @Test
    void shouldMapOptimisticLockExceptionToRfc7807Conflict409() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(problemDetailFactory);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments");
        request.setAttribute("dev.ledgerpulse.core.web.CorrelationIdFilter.CORRELATION_ID", "corr-opt-409");

        var response = handler.handleOptimisticLockConflict(
                new ObjectOptimisticLockingFailureException("Account", UUID.randomUUID()),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody().getType().toString())
                .isEqualTo("https://ledgerpulse.dev/errors/optimistic-lock-conflict");
        assertThat(response.getBody().getTitle()).isEqualTo("Optimistic Lock Conflict");
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
