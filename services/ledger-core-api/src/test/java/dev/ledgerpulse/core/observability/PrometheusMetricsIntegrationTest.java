package dev.ledgerpulse.core.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerpulse.core.auth.service.DevIdentitySeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ledgerpulse_observability_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS ledger_core"
})
class PrometheusMetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldExposeCustomPrometheusMetricsAndCircuitBreakerStateOnActuatorPrometheus() throws Exception {
        MvcResult paymentResult = mockMvc.perform(post("/api/v1/payments")
                        .header("X-API-Key", DevIdentitySeeder.DEV_MERCHANT_API_KEY)
                        .header("Idempotency-Key", "prom-pay-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "payerId", "payer-prometheus-01",
                                "amountCents", 12_000L,
                                "currency", "BRL",
                                "paymentMethod", "PIX",
                                "description", "Prometheus metrics payment"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode paymentJson = objectMapper.readTree(paymentResult.getResponse().getContentAsString());
        String paymentId = paymentJson.get("id").asText();

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                        .header("X-API-Key", DevIdentitySeeder.DEV_MERCHANT_API_KEY)
                        .header("Idempotency-Key", "prom-refund-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\":3000,\"reason\":\"Prometheus partial refund\"}"))
                .andExpect(status().isCreated());

        String prometheusBody = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(prometheusBody).contains("ledger_payments_total");
        assertThat(prometheusBody).contains("ledger_refunds_total");
        assertThat(prometheusBody).contains("risk_evaluation_latency_seconds_bucket");
        assertThat(prometheusBody).contains("risk_evaluation_latency_seconds_sum");
        assertThat(prometheusBody).contains("risk_evaluation_latency_seconds_count");
        assertThat(prometheusBody).containsPattern("circuit_breaker_state\\{[^}]*name=\"riskEngine\"[^}]*\\}\\s+0\\.0");
        assertThat(prometheusBody).contains("resilience4j_circuitbreaker_");
    }
}
