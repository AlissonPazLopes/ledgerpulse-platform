package dev.ledgerpulse.core.ledger.api;

import dev.ledgerpulse.core.ledger.domain.AccountType;
import dev.ledgerpulse.core.ledger.domain.EntryDirection;
import dev.ledgerpulse.core.ledger.domain.LedgerEntry;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID accountId,
        AccountType accountType,
        EntryDirection direction,
        long amountCents,
        long balanceAfterCents,
        Instant createdAt
) {
    public static LedgerEntryResponse from(LedgerEntry entry, AccountType accountType) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getAccountId(),
                accountType,
                entry.getDirection(),
                entry.getAmountCents(),
                entry.getBalanceAfterCents(),
                entry.getCreatedAt()
        );
    }
}
