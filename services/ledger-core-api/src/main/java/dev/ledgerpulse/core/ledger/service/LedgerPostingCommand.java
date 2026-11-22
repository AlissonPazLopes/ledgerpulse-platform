package dev.ledgerpulse.core.ledger.service;

import dev.ledgerpulse.core.ledger.domain.EntryDirection;
import dev.ledgerpulse.core.ledger.domain.LedgerReferenceType;

import java.util.List;
import java.util.UUID;

public record LedgerPostingCommand(
        LedgerReferenceType referenceType,
        UUID referenceId,
        String description,
        String currency,
        String correlationId,
        List<LedgerLeg> legs
) {
    public record LedgerLeg(
            UUID accountId,
            EntryDirection direction,
            long amountCents
    ) {
    }
}
