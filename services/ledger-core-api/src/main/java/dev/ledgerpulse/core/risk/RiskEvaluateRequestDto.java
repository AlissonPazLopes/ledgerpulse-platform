package dev.ledgerpulse.core.risk;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record RiskEvaluateRequestDto(
        @JsonProperty("payment_id") UUID paymentId,
        @JsonProperty("merchant_id") UUID merchantId,
        @JsonProperty("payer_id") String payerId,
        @JsonProperty("amount_cents") long amountCents,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("ip_country") String ipCountry,
        @JsonProperty("velocity_1h") int velocity1h,
        @JsonProperty("hour_of_day") int hourOfDay,
        @JsonProperty("account_age_days") int accountAgeDays
) {
}
