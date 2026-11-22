package dev.ledgerpulse.core.risk.review;

import dev.ledgerpulse.core.payment.domain.PaymentStatus;
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
@Table(name = "risk_review_decisions", schema = "ledger_core")
public class RiskReviewDecision {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, unique = true, updatable = false)
    private UUID paymentId;

    @Column(name = "reviewer_user_id", nullable = false, updatable = false)
    private UUID reviewerUserId;

    @Column(name = "reviewer_email", nullable = false, updatable = false, length = 160)
    private String reviewerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, updatable = false, length = 20)
    private RiskReviewDecisionType decision;

    @Column(name = "justification", nullable = false, updatable = false, length = 500)
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, updatable = false, length = 30)
    private PaymentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "resulting_status", nullable = false, updatable = false, length = 30)
    private PaymentStatus resultingStatus;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RiskReviewDecision() {
    }

    public RiskReviewDecision(
            UUID id,
            UUID paymentId,
            UUID reviewerUserId,
            String reviewerEmail,
            RiskReviewDecisionType decision,
            String justification,
            PaymentStatus previousStatus,
            PaymentStatus resultingStatus,
            String correlationId
    ) {
        this.id = id;
        this.paymentId = paymentId;
        this.reviewerUserId = reviewerUserId;
        this.reviewerEmail = reviewerEmail;
        this.decision = decision;
        this.justification = justification;
        this.previousStatus = previousStatus;
        this.resultingStatus = resultingStatus;
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

    public UUID getReviewerUserId() {
        return reviewerUserId;
    }

    public String getReviewerEmail() {
        return reviewerEmail;
    }

    public RiskReviewDecisionType getDecision() {
        return decision;
    }

    public String getJustification() {
        return justification;
    }

    public PaymentStatus getPreviousStatus() {
        return previousStatus;
    }

    public PaymentStatus getResultingStatus() {
        return resultingStatus;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
