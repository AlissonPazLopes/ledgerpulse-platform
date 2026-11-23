package dev.ledgerpulse.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries", schema = "webhooks")
public class WebhookDelivery {

    public static final int DEFAULT_MAX_ATTEMPTS = 4;
    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "endpoint_id", nullable = false, updatable = false)
    private UUID endpointId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 80)
    private String eventType;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 64)
    private String correlationId;

    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WebhookDeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "last_latency_ms")
    private Long lastLatencyMs;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "signature_header", length = 255)
    private String signatureHeader;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(
            UUID id,
            UUID eventId,
            UUID endpointId,
            UUID merchantId,
            String eventType,
            String correlationId,
            String payloadJson,
            int maxAttempts
    ) {
        this.id = id;
        this.eventId = eventId;
        this.endpointId = endpointId;
        this.merchantId = merchantId;
        this.eventType = eventType;
        this.correlationId = correlationId;
        this.payloadJson = payloadJson;
        this.status = WebhookDeliveryStatus.PENDING;
        this.attemptCount = 0;
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = this.createdAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void recordAttemptOutcome(
            int attemptNumber,
            Integer httpStatus,
            long latencyMs,
            String signatureHeader,
            String errorMessage,
            boolean recoverableFailure,
            boolean forceDlq,
            Instant attemptedAt
    ) {
        this.attemptCount = attemptNumber;
        this.lastHttpStatus = httpStatus;
        this.lastLatencyMs = Math.max(0L, latencyMs);
        this.signatureHeader = signatureHeader;
        this.updatedAt = attemptedAt;

        if (isSuccessStatus(httpStatus)) {
            this.status = WebhookDeliveryStatus.DELIVERED;
            this.deliveredAt = attemptedAt;
            this.lastError = null;
            return;
        }

        this.lastError = truncate(errorMessage);
        if (forceDlq || !recoverableFailure || attemptNumber >= this.maxAttempts) {
            this.status = WebhookDeliveryStatus.DLQ;
        } else {
            this.status = WebhookDeliveryStatus.RETRYING;
        }
    }

    public static boolean isSuccessStatus(Integer httpStatus) {
        return httpStatus != null && httpStatus >= 200 && httpStatus < 300;
    }

    public static boolean isRecoverableStatus(Integer httpStatus) {
        if (httpStatus == null) {
            return true; // Network timeout / connection failure is recoverable
        }
        if (httpStatus >= 500 || httpStatus == 408 || httpStatus == 429) {
            return true;
        }
        return httpStatus < 400;
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

    public UUID getEventId() {
        return eventId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public WebhookDeliveryStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Integer getLastHttpStatus() {
        return lastHttpStatus;
    }

    public Long getLastLatencyMs() {
        return lastLatencyMs;
    }

    public String getLastError() {
        return lastError;
    }

    public String getSignatureHeader() {
        return signatureHeader;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
