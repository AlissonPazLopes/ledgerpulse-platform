package dev.ledgerpulse.webhook.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnServiceHealthWithVirtualThreadsEnabledAndCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .header("X-Correlation-Id", "11111111-2222-3333-4444-555555555555"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "11111111-2222-3333-4444-555555555555"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("webhook-dispatcher"))
                .andExpect(jsonPath("$.version").value("0.1.0"))
                .andExpect(jsonPath("$.virtualThreadsEnabled").value(true))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void shouldExposeActuatorHealthUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
