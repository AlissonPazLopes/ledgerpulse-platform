package dev.ledgerpulse.core.risk;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.ledgerpulse.core.payment.domain.RiskDecision;

import java.util.List;

public record RiskEvaluateResponseDto(
        @JsonProperty("risk_score") int riskScore,
        @JsonProperty("decision") RiskDecision decision,
        @JsonProperty("reason_codes") List<String> reasonCodes,
        @JsonProperty("model_version") String modelVersion
) {
}
