package dev.ledgerpulse.core.refund.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_refunds", schema = "ledger_core")
public class PaymentRefund {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "ledger_transaction_id", nullable = false, updatable = false)
    private UUID ledgerTransactionId;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @Column(name = "platform_fee_reversed_cents", nullable = false, updatable = false)
    private long platformFeeReversedCents;

    @Column(name = "net_merchant_reversed_cents", nullable = false, updatable = false)
    private long netMerchantReversedCents;

    @Column(name = "recipient_reversals_json", nullable = false, updatable = false)
    private String recipientReversalsJson;

    @Column(name = "reason", nullable = false, updatable = false, length = 255)
    private String reason;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentRefund() {
    }

    public PaymentRefund(
            UUID id,
            UUID paymentId,
            UUID merchantId,
            UUID ledgerTransactionId,
            long amountCents,
            long platformFeeReversedCents,
            long netMerchantReversedCents,
            String recipientReversalsJson,
            String reason,
            String idempotencyKey,
            String correlationId
    ) {
        this.id = id;
        this.paymentId = paymentId;
        this.merchantId = merchantId;
        this.ledgerTransactionId = ledgerTransactionId;
        this.amountCents = amountCents;
        this.platformFeeReversedCents = platformFeeReversedCents;
        this.netMerchantReversedCents = netMerchantReversedCents;
        this.recipientReversalsJson = recipientReversalsJson == null ? "[]" : recipientReversalsJson;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.correlationId = correlationId;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public UUID getLedgerTransactionId() {
        return ledgerTransactionId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public long getPlatformFeeReversedCents() {
        return platformFeeReversedCents;
    }

    public long getNetMerchantReversedCents() {
        return netMerchantReversedCents;
    }

    public String getRecipientReversalsJson() {
        return recipientReversalsJson;
    }

    public String getReason() {
        return reason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
