package dev.ledgerpulse.core.payment.domain;

import dev.ledgerpulse.core.web.DomainValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", schema = "ledger_core")
public class Payment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "payer_id", nullable = false, updatable = false, length = 120)
    private String payerId;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @Column(name = "platform_fee_cents", nullable = false)
    private long platformFeeCents;

    @Column(name = "net_merchant_cents", nullable = false)
    private long netMerchantCents;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_decision", length = 20)
    private RiskDecision riskDecision;

    @Column(name = "reason_codes_json", nullable = false)
    private String reasonCodesJson;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 64)
    private String correlationId;

    @Column(name = "ledger_transaction_id")
    private UUID ledgerTransactionId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(
            UUID id,
            UUID merchantId,
            String payerId,
            long amountCents,
            long platformFeeCents,
            long netMerchantCents,
            String currency,
            PaymentMethod paymentMethod,
            String description,
            String idempotencyKey,
            String correlationId
    ) {
        if (amountCents <= 0) {
            throw new DomainValidationException("amountCents deve ser maior que zero.");
        }
        this.id = id;
        this.merchantId = merchantId;
        this.payerId = payerId;
        this.amountCents = amountCents;
        this.platformFeeCents = platformFeeCents;
        this.netMerchantCents = netMerchantCents;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.description = description;
        this.status = PaymentStatus.CREATED;
        this.reasonCodesJson = "[]";
        this.idempotencyKey = idempotencyKey;
        this.correlationId = correlationId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void transitionTo(PaymentStatus next) {
        if (!this.status.canTransitionTo(next)) {
            throw new DomainValidationException(
                    "Transição de status inválida para pagamento " + this.id + ": " + this.status + " -> " + next
            );
        }
        this.status = next;
    }

    public void recordRiskAssessment(int riskScore, RiskDecision riskDecision, String reasonCodesJson) {
        this.riskScore = riskScore;
        this.riskDecision = riskDecision;
        this.reasonCodesJson = reasonCodesJson == null ? "[]" : reasonCodesJson;
    }

    public void attachLedgerTransaction(UUID ledgerTransactionId) {
        this.ledgerTransactionId = ledgerTransactionId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getPayerId() {
        return payerId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public long getPlatformFeeCents() {
        return platformFeeCents;
    }

    public long getNetMerchantCents() {
        return netMerchantCents;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getDescription() {
        return description;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public RiskDecision getRiskDecision() {
        return riskDecision;
    }

    public String getReasonCodesJson() {
        return reasonCodesJson;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public UUID getLedgerTransactionId() {
        return ledgerTransactionId;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
