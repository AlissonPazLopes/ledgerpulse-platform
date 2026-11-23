package dev.ledgerpulse.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_delivery_attempts", schema = "webhooks")
public class WebhookDeliveryAttempt {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Column(name = "http_status", updatable = false)
    private Integer httpStatus;

    @Column(name = "latency_ms", nullable = false, updatable = false)
    private long latencyMs;

    @Column(name = "signature_header", nullable = false, updatable = false, length = 255)
    private String signatureHeader;

    @Column(name = "error_message", updatable = false)
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    protected WebhookDeliveryAttempt() {
    }

    public WebhookDeliveryAttempt(
            UUID id,
            UUID deliveryId,
            int attemptNumber,
            Integer httpStatus,
            long latencyMs,
            String signatureHeader,
            String errorMessage,
            Instant attemptedAt
    ) {
        this.id = id;
        this.deliveryId = deliveryId;
        this.attemptNumber = attemptNumber;
        this.httpStatus = httpStatus;
        this.latencyMs = Math.max(0L, latencyMs);
        this.signatureHeader = signatureHeader;
        this.errorMessage = truncate(errorMessage);
        this.attemptedAt = attemptedAt;
    }

    @PrePersist
    void onCreate() {
        if (this.attemptedAt == null) {
            this.attemptedAt = Instant.now();
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeliveryId() {
        return deliveryId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getSignatureHeader() {
        return signatureHeader;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
