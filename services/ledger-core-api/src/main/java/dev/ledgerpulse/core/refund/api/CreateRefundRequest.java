package dev.ledgerpulse.core.refund.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRefundRequest(
        @Min(value = 1, message = "amountCents deve ser maior que zero.")
        Long amountCents,

        @NotBlank(message = "reason é obrigatório.")
        @Size(min = 3, max = 255, message = "reason deve ter entre 3 e 255 caracteres.")
        String reason
) {
}
