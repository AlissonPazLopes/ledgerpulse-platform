package dev.ledgerpulse.core.reconciliation.api;

import dev.ledgerpulse.core.reconciliation.domain.ReconciliationStatus;
import dev.ledgerpulse.core.reconciliation.domain.ReconciliationTrigger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReconciliationReportResponse(
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
        List<String> findings,
        String correlationId,
        Instant createdAt
) {
}
