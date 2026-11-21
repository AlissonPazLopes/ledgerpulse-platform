package dev.ledgerpulse.core.outbox;

public interface OutboxEventPublisher {

    void publish(OutboxEvent event);
}
