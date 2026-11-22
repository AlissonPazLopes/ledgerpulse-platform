package dev.ledgerpulse.core.ledger.api;

import dev.ledgerpulse.core.ledger.domain.Account;
import dev.ledgerpulse.core.ledger.domain.AccountStatus;
import dev.ledgerpulse.core.ledger.domain.AccountType;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        UUID merchantId,
        AccountType accountType,
        String holderName,
        String currency,
        long availableBalanceCents,
        long pendingBalanceCents,
        AccountStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getMerchantId(),
                account.getAccountType(),
                account.getHolderName(),
                account.getCurrency(),
                account.getAvailableBalanceCents(),
                account.getPendingBalanceCents(),
                account.getStatus(),
                account.getVersion(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
