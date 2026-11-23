package dev.ledgerpulse.webhook.domain;

public enum WebhookDeliveryStatus {
    PENDING,
    RETRYING,
    DELIVERED,
    DLQ
}
