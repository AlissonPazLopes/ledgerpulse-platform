package dev.ledgerpulse.core.ledger.domain;

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
@Table(name = "accounts", schema = "ledger_core")
public class Account {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private AccountType accountType;

    @Column(name = "holder_name", nullable = false, length = 160)
    private String holderName;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "available_balance_cents", nullable = false)
    private long availableBalanceCents;

    @Column(name = "pending_balance_cents", nullable = false)
    private long pendingBalanceCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
    }

    public Account(
            UUID id,
            UUID merchantId,
            AccountType accountType,
            String holderName,
            String currency,
            AccountStatus status
    ) {
        if (id == null) {
            throw new DomainValidationException("Account id must not be null.");
        }
        if (accountType == null) {
            throw new DomainValidationException("Account type must not be null.");
        }
        if ((accountType == AccountType.MERCHANT || accountType == AccountType.RECIPIENT) && merchantId == null) {
            throw new DomainValidationException("merchantId é obrigatório para contas MERCHANT e RECIPIENT.");
        }
        if (!"BRL".equals(currency)) {
            throw new DomainValidationException("Moeda não suportada: apenas BRL é aceita no v1.");
        }
        this.id = id;
        this.merchantId = merchantId;
        this.accountType = accountType;
        this.holderName = holderName;
        this.currency = currency;
        this.availableBalanceCents = 0L;
        this.pendingBalanceCents = 0L;
        this.status = status == null ? AccountStatus.ACTIVE : status;
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

    /**
     * Applies an immutable ledger entry to this account balance.
     * CREDIT adds amountCents; DEBIT subtracts amountCents.
     * Only systemic ESCROW accounts may hold a negative balance.
     */
    public long applyEntry(EntryDirection direction, long amountCents) {
        if (direction == null) {
            throw new DomainValidationException("Direção do lançamento é obrigatória.");
        }
        if (amountCents <= 0) {
            throw new DomainValidationException("Valor do lançamento deve ser estritamente positivo (amountCents > 0).");
        }
        if (this.status != AccountStatus.ACTIVE) {
            throw new DomainValidationException("Conta " + this.id + " está bloqueada (BLOCKED) e não aceita movimentações.");
        }

        long nextBalance = switch (direction) {
            case CREDIT -> Math.addExact(this.availableBalanceCents, amountCents);
            case DEBIT -> {
                long candidate = Math.subtractExact(this.availableBalanceCents, amountCents);
                if (candidate < 0 && this.accountType != AccountType.ESCROW) {
                    throw new DomainValidationException(
                            "Saldo insuficiente na conta " + this.id + " (" + this.accountType + ")."
                    );
                }
                yield candidate;
            }
        };

        this.availableBalanceCents = nextBalance;
        this.updatedAt = Instant.now();
        return nextBalance;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getCurrency() {
        return currency;
    }

    public long getAvailableBalanceCents() {
        return availableBalanceCents;
    }

    public long getPendingBalanceCents() {
        return pendingBalanceCents;
    }

    public AccountStatus getStatus() {
        return status;
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
