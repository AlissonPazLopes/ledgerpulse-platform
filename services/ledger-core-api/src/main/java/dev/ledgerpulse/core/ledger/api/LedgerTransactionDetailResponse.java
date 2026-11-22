package dev.ledgerpulse.core.ledger.api;

import dev.ledgerpulse.core.ledger.domain.LedgerReferenceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LedgerTransactionDetailResponse(
        UUID id,
        LedgerReferenceType referenceType,
        UUID referenceId,
        String description,
        String currency,
        long totalDebitCents,
        long totalCreditCents,
        boolean balanced,
        String correlationId,
        Instant createdAt,
        List<LedgerEntryResponse> entries
) {
}
