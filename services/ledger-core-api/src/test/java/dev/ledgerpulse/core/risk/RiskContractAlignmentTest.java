package dev.ledgerpulse.core.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerpulse.core.payment.domain.RiskDecision;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RiskContractAlignmentTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeAndDeserializeFrozenSnakeCaseCrossContractFixtureExactly() throws Exception {
        Path contractPath = resolveContractFixturePath();
        assertThat(contractPath).exists();

        JsonNode root = objectMapper.readTree(Files.readString(contractPath));
        JsonNode requestNode = root.get("request");

        RiskEvaluateRequestDto dto = objectMapper.treeToValue(requestNode, RiskEvaluateRequestDto.class);
        assertThat(dto.paymentId()).isEqualTo(UUID.fromString("e1d2c3b4-5a6b-4c7d-8e9f-001122334455"));
        assertThat(dto.merchantId()).isEqualTo(UUID.fromString("7b92c3e0-1f42-4876-a1e2-901283471234"));
        assertThat(dto.payerId()).isEqualTo("payer-98765");
        assertThat(dto.amountCents()).isEqualTo(15000L);
        assertThat(dto.paymentMethod()).isEqualTo("CARD");
        assertThat(dto.ipCountry()).isEqualTo("BR");
        assertThat(dto.velocity1h()).isEqualTo(1);
        assertThat(dto.hourOfDay()).isEqualTo(14);
        assertThat(dto.accountAgeDays()).isEqualTo(180);

        String serialized = objectMapper.writeValueAsString(dto);
        JsonNode reserialized = objectMapper.readTree(serialized);
        assertThat(reserialized).isEqualTo(requestNode);

        RiskEvaluateResponseDto approveResp = objectMapper.treeToValue(
                root.get("responses").get("approve"),
                RiskEvaluateResponseDto.class
        );
        assertThat(approveResp.riskScore()).isEqualTo(12);
        assertThat(approveResp.decision()).isEqualTo(RiskDecision.APPROVE);
        assertThat(approveResp.reasonCodes()).isEmpty();
        assertThat(approveResp.modelVersion()).isEqualTo("v1.0.0");

        RiskEvaluateResponseDto reviewResp = objectMapper.treeToValue(
                root.get("responses").get("review"),
                RiskEvaluateResponseDto.class
        );
        assertThat(reviewResp.riskScore()).isEqualTo(58);
        assertThat(reviewResp.decision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(reviewResp.reasonCodes()).containsExactly("NIGHT_ANOMALY", "ML_ANOMALY_DETECTED");

        RiskEvaluateResponseDto blockResp = objectMapper.treeToValue(
                root.get("responses").get("block"),
                RiskEvaluateResponseDto.class
        );
        assertThat(blockResp.riskScore()).isEqualTo(92);
        assertThat(blockResp.decision()).isEqualTo(RiskDecision.BLOCK);
        assertThat(blockResp.reasonCodes())
                .containsExactly("VELOCITY_SPIKE", "HIGH_TICKET_NEW_ACCOUNT", "ML_HIGH_FRAUD_PROBABILITY");
    }

    private Path resolveContractFixturePath() {
        Path fromServiceRoot = Path.of("..", "..", "docs", "contracts", "contract-samples", "risk-evaluate-contract.json");
        if (Files.exists(fromServiceRoot)) {
            return fromServiceRoot;
        }
        return Path.of("docs", "contracts", "contract-samples", "risk-evaluate-contract.json");
    }
}
