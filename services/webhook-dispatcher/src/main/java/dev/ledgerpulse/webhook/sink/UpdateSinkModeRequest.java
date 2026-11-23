package dev.ledgerpulse.webhook.sink;

import jakarta.validation.constraints.NotNull;

public record UpdateSinkModeRequest(
        @NotNull(message = "mode é obrigatório")
        SinkMode mode
) {
}
