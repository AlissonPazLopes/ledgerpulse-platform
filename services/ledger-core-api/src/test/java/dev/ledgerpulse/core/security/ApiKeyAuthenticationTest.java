package dev.ledgerpulse.core.security;

import dev.ledgerpulse.core.auth.domain.Merchant;
import dev.ledgerpulse.core.auth.domain.MerchantStatus;
import dev.ledgerpulse.core.auth.repository.MerchantRepository;
import dev.ledgerpulse.core.auth.service.DevIdentitySeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiKeyAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantRepository merchantRepository;

    @Test
    void shouldMatchDocumentedSha256HashForDemoMerchantKey() {
        assertThat(ApiKeyHasher.sha256Hex(DevIdentitySeeder.DEV_MERCHANT_API_KEY))
                .isEqualTo("c7c6a21450462cbc3ab9eba66cb46fc1fa55686c524415346dcc58b55d8ae319");
    }

    @Test
    void shouldAuthenticateDemoMerchantWithValidApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/me")
                        .header("X-API-Key", DevIdentitySeeder.DEV_MERCHANT_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("7b92c3e0-1f42-4876-a1e2-901283471234"))
                .andExpect(jsonPath("$.name").value("Acme Fintech Store"))
                .andExpect(jsonPath("$.defaultFeeBps").value(250))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldReturn401ProblemDetailWhenApiKeyIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/unauthorized"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/v1/merchants/me"))
                .andExpect(jsonPath("$.correlationId", not(blankOrNullString())))
                .andExpect(jsonPath("$.timestamp", not(blankOrNullString())));
    }

    @Test
    void shouldReturn401ProblemDetailWhenApiKeyIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/me")
                        .header("X-API-Key", "lp_invalid_key_999"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @Transactional
    void shouldReturn401WhenMerchantIsSuspended() throws Exception {
        String suspendedKey = "lp_suspended_merchant_test_key";
        merchantRepository.saveAndFlush(new Merchant(
                UUID.randomUUID(),
                "Suspended Merchant Ltd",
                ApiKeyHasher.sha256Hex(suspendedKey),
                300,
                MerchantStatus.SUSPENDED
        ));

        mockMvc.perform(get("/api/v1/merchants/me")
                        .header("X-API-Key", suspendedKey))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void shouldReturn403WhenOperatorJwtAccessesMerchantEndpoint() throws Exception {
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin@ledgerpulse.dev",
                                  "password": "LedgerPulse@2026"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = com.jayway.jsonpath.JsonPath.read(loginResponse, "$.accessToken");

        mockMvc.perform(get("/api/v1/merchants/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }
}
