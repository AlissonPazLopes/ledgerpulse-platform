package dev.ledgerpulse.core.stream;

import dev.ledgerpulse.core.outbox.OutboxEventEnvelope;

public record DomainStreamEvent(OutboxEventEnvelope envelope) {
}
