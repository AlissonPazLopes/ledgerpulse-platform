package dev.ledgerpulse.core.payment.api;

import dev.ledgerpulse.core.payment.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreatePaymentRequest(
        UUID merchantId,

        @NotBlank(message = "must not be blank")
        @Size(max = 120, message = "size must be at most 120")
        String payerId,

        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be greater than 0")
        Long amountCents,

        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO-4217 currency code")
        String currency,

        @NotNull(message = "must not be null")
        PaymentMethod paymentMethod,

        @Size(max = 255, message = "size must be at most 255")
        String description,

        @Valid
        List<PaymentSplitRequest> splits
) {
}
