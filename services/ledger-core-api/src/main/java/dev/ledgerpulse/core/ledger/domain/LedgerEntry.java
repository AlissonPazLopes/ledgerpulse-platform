package dev.ledgerpulse.core.ledger.domain;

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
@Table(name = "ledger_entries", schema = "ledger_core")
public class LedgerEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ledger_transaction_id", nullable = false, updatable = false)
    private UUID ledgerTransactionId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, updatable = false, length = 10)
    private EntryDirection direction;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @Column(name = "balance_after_cents", nullable = false, updatable = false)
    private long balanceAfterCents;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(
            UUID id,
            UUID ledgerTransactionId,
            UUID accountId,
            EntryDirection direction,
            long amountCents,
            long balanceAfterCents
    ) {
        if (id == null || ledgerTransactionId == null || accountId == null || direction == null) {
            throw new DomainValidationException("Campos obrigatórios do lançamento contábil não podem ser nulos.");
        }
        if (amountCents <= 0) {
            throw new DomainValidationException("amountCents do lançamento deve ser maior que zero.");
        }
        this.id = id;
        this.ledgerTransactionId = ledgerTransactionId;
        this.accountId = accountId;
        this.direction = direction;
        this.amountCents = amountCents;
        this.balanceAfterCents = balanceAfterCents;
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

    public UUID getLedgerTransactionId() {
        return ledgerTransactionId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public EntryDirection getDirection() {
        return direction;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public long getBalanceAfterCents() {
        return balanceAfterCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
