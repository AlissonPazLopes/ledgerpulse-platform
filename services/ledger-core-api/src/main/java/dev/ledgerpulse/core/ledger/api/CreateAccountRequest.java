package dev.ledgerpulse.core.ledger.api;

import dev.ledgerpulse.core.ledger.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateAccountRequest(
        UUID merchantId,

        @NotNull(message = "must not be null")
        AccountType accountType,

        @NotBlank(message = "must not be blank")
        @Size(min = 2, max = 160, message = "size must be between 2 and 160")
        String holderName,

        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO-4217 currency code")
        String currency
) {
}
