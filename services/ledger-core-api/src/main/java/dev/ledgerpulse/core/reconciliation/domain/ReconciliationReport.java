package dev.ledgerpulse.core.reconciliation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_reports", schema = "ledger_core")
public class ReconciliationReport {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReconciliationStatus status;

    @Column(name = "checked_accounts_count", nullable = false)
    private int checkedAccountsCount;

    @Column(name = "divergent_accounts_count", nullable = false)
    private int divergentAccountsCount;

    @Column(name = "checked_transactions_count", nullable = false)
    private int checkedTransactionsCount;

    @Column(name = "unbalanced_transactions_count", nullable = false)
    private int unbalancedTransactionsCount;

    @Column(name = "total_settled_payments_cents", nullable = false)
    private long totalSettledPaymentsCents;

    @Column(name = "total_ledger_settlements_cents", nullable = false)
    private long totalLedgerSettlementsCents;

    @Column(name = "total_refunds_cents", nullable = false)
    private long totalRefundsCents;

    @Column(name = "total_ledger_refunds_cents", nullable = false)
    private long totalLedgerRefundsCents;

    @Column(name = "discrepancy_cents", nullable = false)
    private long discrepancyCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 20)
    private ReconciliationTrigger triggerSource;

    @Column(name = "findings_json", nullable = false)
    private String findingsJson;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReconciliationReport() {
    }

    public ReconciliationReport(
            UUID id,
            ReconciliationStatus status,
            int checkedAccountsCount,
            int divergentAccountsCount,
            int checkedTransactionsCount,
            int unbalancedTransactionsCount,
            long totalSettledPaymentsCents,
            long totalLedgerSettlementsCents,
            long totalRefundsCents,
            long totalLedgerRefundsCents,
            long discrepancyCents,
            ReconciliationTrigger triggerSource,
            String findingsJson,
            String correlationId
    ) {
        this.id = id;
        this.status = status;
        this.checkedAccountsCount = checkedAccountsCount;
        this.divergentAccountsCount = divergentAccountsCount;
        this.checkedTransactionsCount = checkedTransactionsCount;
        this.unbalancedTransactionsCount = unbalancedTransactionsCount;
        this.totalSettledPaymentsCents = totalSettledPaymentsCents;
        this.totalLedgerSettlementsCents = totalLedgerSettlementsCents;
        this.totalRefundsCents = totalRefundsCents;
        this.totalLedgerRefundsCents = totalLedgerRefundsCents;
        this.discrepancyCents = discrepancyCents;
        this.triggerSource = triggerSource;
        this.findingsJson = findingsJson == null ? "[]" : findingsJson;
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

    public ReconciliationStatus getStatus() {
        return status;
    }

    public int getCheckedAccountsCount() {
        return checkedAccountsCount;
    }

    public int getDivergentAccountsCount() {
        return divergentAccountsCount;
    }

    public int getCheckedTransactionsCount() {
        return checkedTransactionsCount;
    }

    public int getUnbalancedTransactionsCount() {
        return unbalancedTransactionsCount;
    }

    public long getTotalSettledPaymentsCents() {
        return totalSettledPaymentsCents;
    }

    public long getTotalLedgerSettlementsCents() {
        return totalLedgerSettlementsCents;
    }

    public long getTotalRefundsCents() {
        return totalRefundsCents;
    }

    public long getTotalLedgerRefundsCents() {
        return totalLedgerRefundsCents;
    }

    public long getDiscrepancyCents() {
        return discrepancyCents;
    }

    public ReconciliationTrigger getTriggerSource() {
        return triggerSource;
    }

    public String getFindingsJson() {
        return findingsJson;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
