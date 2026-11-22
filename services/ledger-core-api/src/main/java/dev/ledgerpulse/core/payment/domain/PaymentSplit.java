package dev.ledgerpulse.core.payment.domain;

import dev.ledgerpulse.core.web.DomainValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "payment_splits", schema = "ledger_core")
public class PaymentSplit {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "recipient_account_id", nullable = false, updatable = false)
    private UUID recipientAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, updatable = false, length = 20)
    private SplitType splitType;

    @Column(name = "percentage_bps", updatable = false)
    private Integer percentageBps;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentSplit() {
    }

    public PaymentSplit(
            UUID id,
            UUID paymentId,
            UUID recipientAccountId,
            SplitType splitType,
            Integer percentageBps,
            long amountCents
    ) {
        if (amountCents <= 0) {
            throw new DomainValidationException("Valor do split deve ser maior que zero.");
        }
        this.id = id;
        this.paymentId = paymentId;
        this.recipientAccountId = recipientAccountId;
        this.splitType = splitType;
        this.percentageBps = percentageBps;
        this.amountCents = amountCents;
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

    public UUID getRecipientAccountId() {
        return recipientAccountId;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public Integer getPercentageBps() {
        return percentageBps;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
