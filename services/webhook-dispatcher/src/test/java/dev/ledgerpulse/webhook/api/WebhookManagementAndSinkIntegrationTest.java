package dev.ledgerpulse.webhook.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerpulse.webhook.domain.WebhookDelivery;
import dev.ledgerpulse.webhook.domain.WebhookEndpoint;
import dev.ledgerpulse.webhook.domain.WebhookEndpointStatus;
import dev.ledgerpulse.webhook.repository.WebhookDeliveryRepository;
import dev.ledgerpulse.webhook.repository.WebhookEndpointRepository;
import dev.ledgerpulse.webhook.security.AuthenticatedOperator;
import dev.ledgerpulse.webhook.security.JwtService;
import dev.ledgerpulse.webhook.security.UserRole;
import dev.ledgerpulse.webhook.sink.SinkMode;
import dev.ledgerpulse.webhook.sink.WebhookSinkStateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookManagementAndSinkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private WebhookSinkStateService sinkStateService;

    @Autowired
    private WebhookEndpointRepository endpointRepository;

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;

    private String adminToken;
    private String analystToken;
    private String viewerToken;

    @BeforeEach
    void setUp() {
        sinkStateService.updateMode(SinkMode.OK_200);
        adminToken = tokenFor(UserRole.ADMIN, "admin@ledgerpulse.dev");
        analystToken = tokenFor(UserRole.ANALYST, "analyst@ledgerpulse.dev");
        viewerToken = tokenFor(UserRole.VIEWER, "viewer@ledgerpulse.dev");
    }

    @AfterEach
    void tearDown() {
        sinkStateService.updateMode(SinkMode.OK_200);
    }

    @Test
    void shouldRequireJwtForProtectedEndpointsAndAllowPublicSinkTest() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/endpoints"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/unauthorized"));

        mockMvc.perform(get("/api/v1/webhooks/deliveries"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/sink/mode"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/sink/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"c7a615f1-82b3-4e11-9c2a-5d2d1b63e701\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true))
                .andExpect(jsonPath("$.mode").value("OK_200"));
    }

    @Test
    void shouldEnforceRbacAndNeverExposePlaintextSigningSecret() throws Exception {
        String rawSecret = "whsec_super_secret_plain_2026";
        String body = objectMapper.writeValueAsString(Map.of(
                "merchantId", UUID.randomUUID().toString(),
                "targetUrl", "http://localhost:8082/api/v1/sink/test",
                "secret", rawSecret,
                "description", "RBAC Test Endpoint"
        ));

        // VIEWER & ANALYST blocked on POST /endpoints (ADMIN only)
        mockMvc.perform(post("/api/v1/webhooks/endpoints")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/forbidden"));

        mockMvc.perform(post("/api/v1/webhooks/endpoints")
                        .header("Authorization", "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        // VIEWER blocked on PUT /sink/mode & POST /dlq/{id}/replay
        mockMvc.perform(put("/api/v1/sink/mode")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"FAIL_500\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/webhooks/dlq/" + UUID.randomUUID() + "/replay")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        // ADMIN creates endpoint -> secretPreview masked, plaintext secret NEVER leaked
        mockMvc.perform(post("/api/v1/webhooks/endpoints")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secretPreview").value("whsec_****2026"))
                .andExpect(content().string(not(containsString(rawSecret))));

        // VIEWER can read endpoints & deliveries & sink mode
        mockMvc.perform(get("/api/v1/webhooks/endpoints")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(content().string(not(containsString(rawSecret))));
    }

    @Test
    void shouldSwitchSinkModesAndReturnRfc7807ErrorsForInvalidReplayOrInput() throws Exception {
        // FAIL_500 mode
        mockMvc.perform(put("/api/v1/sink/mode")
                        .header("Authorization", "Bearer " + analystToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"FAIL_500\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("FAIL_500"));

        mockMvc.perform(post("/api/v1/sink/test")
                        .header("X-LedgerPulse-Signature", "t=1700000000,v1=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"evt-500\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.received").value(false))
                .andExpect(jsonPath("$.mode").value("FAIL_500"))
                .andExpect(jsonPath("$.signaturePresent").value(true));

        // TIMEOUT mode -> 504
        mockMvc.perform(put("/api/v1/sink/mode")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TIMEOUT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("TIMEOUT"));

        mockMvc.perform(post("/api/v1/sink/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.received").value(false))
                .andExpect(jsonPath("$.mode").value("TIMEOUT"));

        // Validation error on invalid URL
        mockMvc.perform(post("/api/v1/webhooks/endpoints")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "merchantId", UUID.randomUUID().toString(),
                                "targetUrl", "ftp://invalid-scheme.local",
                                "secret", "whsec_valid_length_2026"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/validation-error"));

        // Replay unknown delivery -> 404
        mockMvc.perform(post("/api/v1/webhooks/dlq/" + UUID.randomUUID() + "/replay")
                        .header("Authorization", "Bearer " + analystToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/resource-not-found"));

        // Replay non-DLQ delivery -> 409 invalid-delivery-state
        WebhookEndpoint endpoint = endpointRepository.saveAndFlush(new WebhookEndpoint(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "http://localhost:8082/api/v1/sink/test",
                "whsec_conflict_test_2026",
                "Conflict test",
                WebhookEndpointStatus.ACTIVE
        ));
        WebhookDelivery pendingDelivery = deliveryRepository.saveAndFlush(new WebhookDelivery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                endpoint.getId(),
                endpoint.getMerchantId(),
                "payment.settled",
                UUID.randomUUID().toString(),
                "{}",
                4
        ));

        mockMvc.perform(post("/api/v1/webhooks/dlq/" + pendingDelivery.getId() + "/replay")
                        .header("Authorization", "Bearer " + analystToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://ledgerpulse.dev/errors/invalid-delivery-state"));
    }

    private String tokenFor(UserRole role, String email) {
        return jwtService.generateToken(new AuthenticatedOperator(
                UUID.randomUUID(),
                "Operator " + role.name(),
                email,
                role
        ));
    }
}
