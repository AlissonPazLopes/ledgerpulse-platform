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
@Table(name = "webhook_endpoints", schema = "webhooks")
public class WebhookEndpoint {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "target_url", nullable = false, length = 500)
    private String targetUrl;

    @Column(name = "signing_secret", nullable = false, length = 255)
    private String signingSecret;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WebhookEndpointStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookEndpoint() {
    }

    public WebhookEndpoint(
            UUID id,
            UUID merchantId,
            String targetUrl,
            String signingSecret,
            String description,
            WebhookEndpointStatus status
    ) {
        this.id = id;
        this.merchantId = merchantId;
        this.targetUrl = targetUrl;
        this.signingSecret = signingSecret;
        this.description = description;
        this.status = status == null ? WebhookEndpointStatus.ACTIVE : status;
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

    public String getMaskedSecretPreview() {
        return maskSecret(this.signingSecret);
    }

    public static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "****";
        }
        if (secret.startsWith("whsec_") && secret.length() >= 10) {
            return "whsec_****" + secret.substring(secret.length() - 4);
        }
        if (secret.length() > 8) {
            return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
        }
        return "****";
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public String getSigningSecret() {
        return signingSecret;
    }

    public String getDescription() {
        return description;
    }

    public WebhookEndpointStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
