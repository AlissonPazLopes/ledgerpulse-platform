package dev.ledgerpulse.core.risk.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RiskReviewDecisionRequest(
        @NotNull(message = "must not be null")
        RiskReviewDecisionType decision,

        @NotBlank(message = "must not be blank")
        @Size(min = 5, max = 500, message = "size must be between 5 and 500")
        String justification
) {
}
