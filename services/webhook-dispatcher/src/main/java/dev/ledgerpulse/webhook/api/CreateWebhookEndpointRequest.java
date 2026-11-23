package dev.ledgerpulse.webhook.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateWebhookEndpointRequest(
        @NotNull(message = "merchantId é obrigatório")
        UUID merchantId,

        @NotBlank(message = "targetUrl é obrigatória")
        @Size(max = 500, message = "targetUrl deve ter no máximo 500 caracteres")
        String targetUrl,

        @NotBlank(message = "secret é obrigatório")
        @Size(min = 16, max = 255, message = "secret deve ter entre 16 e 255 caracteres")
        String secret,

        @Size(max = 255, message = "description deve ter no máximo 255 caracteres")
        String description
) {
}
