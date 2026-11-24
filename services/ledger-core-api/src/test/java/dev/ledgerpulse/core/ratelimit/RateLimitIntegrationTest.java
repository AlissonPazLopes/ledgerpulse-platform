package dev.ledgerpulse.core.ratelimit;

import dev.ledgerpulse.core.auth.domain.Merchant;
import dev.ledgerpulse.core.auth.domain.MerchantStatus;
import dev.ledgerpulse.core.auth.domain.UserAccount;
import dev.ledgerpulse.core.auth.repository.MerchantRepository;
import dev.ledgerpulse.core.auth.repository.UserAccountRepository;
import dev.ledgerpulse.core.security.ApiKeyHasher;
import dev.ledgerpulse.core.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.store=memory",
        "app.rate-limit.capacity=3",
        "app.rate-limit.refill-tokens=3",
        "app.rate-limit.refill-period-seconds=60"
})
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldAllowUpToCapacityAndReturn429WithRateLimitHeadersOnFourthApiKeyRequest() throws Exception {
        String rawApiKey = "lp_ratelimit_test_" + UUID.randomUUID();
        merchantRepository.saveAndFlush(new Merchant(
                UUID.randomUUID(),
                "Rate Limited Merchant",
                ApiKeyHasher.sha256Hex(rawApiKey),
                250,
                MerchantStatus.ACTIVE
        ));

        for (int expectedRemaining = 2; expectedRemaining >= 0; expectedRemaining--) {
            mockMvc.perform(get("/api/v1/merchants/me")
                            .header("X-API-Key", rawApiKey))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "3"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(expectedRemaining)))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        // 4th request exceeds Token Bucket capacity -> 429 Too Many Requests
        mockMvc.perform(get("/api/v1/merchants/me")
                        .header("X-API-Key", rawApiKey))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-RateLimit-Limit", "3"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("X-RateLimit-Reset"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/rate-limit-exceeded"))
                .andExpect(jsonPath("$.status").value(429));

        // JWT operator calls are not throttled by X-API-Key bucket
        UserAccount admin = userAccountRepository.findByEmailIgnoreCase("admin@ledgerpulse.dev").orElseThrow();
        String adminJwt = jwtService.generateToken(admin);
        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk());
    }
}
