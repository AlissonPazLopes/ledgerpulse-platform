package dev.ledgerpulse.core.reconciliation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledgerpulse.core.ledger.domain.Account;
import dev.ledgerpulse.core.ledger.domain.AccountType;
import dev.ledgerpulse.core.ledger.domain.EntryDirection;
import dev.ledgerpulse.core.ledger.domain.LedgerReferenceType;
import dev.ledgerpulse.core.ledger.domain.LedgerTransaction;
import dev.ledgerpulse.core.ledger.repository.AccountRepository;
import dev.ledgerpulse.core.ledger.repository.LedgerEntryRepository;
import dev.ledgerpulse.core.ledger.repository.LedgerTransactionRepository;
import dev.ledgerpulse.core.outbox.OutboxEventEnvelope;
import dev.ledgerpulse.core.outbox.OutboxService;
import dev.ledgerpulse.core.payment.domain.PaymentStatus;
import dev.ledgerpulse.core.payment.repository.PaymentRepository;
import dev.ledgerpulse.core.reconciliation.api.ReconciliationReportResponse;
import dev.ledgerpulse.core.reconciliation.domain.ReconciliationReport;
import dev.ledgerpulse.core.reconciliation.domain.ReconciliationStatus;
import dev.ledgerpulse.core.reconciliation.domain.ReconciliationTrigger;
import dev.ledgerpulse.core.reconciliation.repository.ReconciliationReportRepository;
import dev.ledgerpulse.core.refund.repository.PaymentRefundRepository;
import dev.ledgerpulse.core.stream.DomainStreamEvent;
import dev.ledgerpulse.core.web.PageResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReconciliationService {

    private static final String CSV_HEADER =
            "reportId,createdAt,status,triggerSource,checkedAccountsCount,divergentAccountsCount,"
                    + "checkedTransactionsCount,unbalancedTransactionsCount,totalSettledPaymentsCents,"
                    + "totalLedgerSettlementsCents,totalRefundsCents,totalLedgerRefundsCents,discrepancyCents";

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private static final EnumSet<PaymentStatus> SETTLED_LIFECYCLE_STATUSES = EnumSet.of(
            PaymentStatus.SETTLED,
            PaymentStatus.PARTIALLY_REFUNDED,
            PaymentStatus.REFUNDED
    );

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final ReconciliationReportRepository reportRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public ReconciliationService(
            AccountRepository accountRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            PaymentRepository paymentRepository,
            PaymentRefundRepository paymentRefundRepository,
            ReconciliationReportRepository reportRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.accountRepository = accountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.paymentRepository = paymentRepository;
        this.paymentRefundRepository = paymentRefundRepository;
        this.reportRepository = reportRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReconciliationReportResponse runReconciliation(
            ReconciliationTrigger triggerSource,
            String correlationId
    ) {
        String effectiveCorrelationId = StringUtils.hasText(correlationId)
                ? correlationId.trim()
                : UUID.randomUUID().toString();

        List<String> findings = new ArrayList<>();
        long discrepancyCents = 0L;

        // 1. Account balances vs sum(CREDIT) - sum(DEBIT) in ledger_entries
        List<Account> accounts = accountRepository.findAll();
        Map<UUID, Long> netEntriesByAccount = new HashMap<>();
        for (Object[] row : ledgerEntryRepository.summarizeByAccountAndDirection()) {
            UUID accountId = (UUID) row[0];
            EntryDirection direction = (EntryDirection) row[1];
            long sumCents = ((Number) row[2]).longValue();
            long signedDelta = direction == EntryDirection.CREDIT ? sumCents : -sumCents;
            netEntriesByAccount.merge(accountId, signedDelta, Math::addExact);
        }

        int divergentAccountsCount = 0;
        for (Account account : accounts) {
            long expectedBalance = netEntriesByAccount.getOrDefault(account.getId(), 0L);
            if (account.getAvailableBalanceCents() != expectedBalance) {
                divergentAccountsCount++;
                long diff = Math.abs(Math.subtractExact(account.getAvailableBalanceCents(), expectedBalance));
                discrepancyCents = Math.addExact(discrepancyCents, diff);
                findings.add("ACCOUNT_DIVERGENCE: accountId=" + account.getId()
                        + " expectedBalanceCents=" + expectedBalance
                        + " actualBalanceCents=" + account.getAvailableBalanceCents());
            }
        }

        // 2. Double-entry balance per LedgerTransaction
        List<LedgerTransaction> transactions = ledgerTransactionRepository.findAll();
        Map<UUID, TxBalanceAccumulator> txSums = new HashMap<>();
        for (Object[] row : ledgerEntryRepository.summarizeByTransactionAndDirection()) {
            UUID txId = (UUID) row[0];
            EntryDirection direction = (EntryDirection) row[1];
            long count = ((Number) row[2]).longValue();
            long sumCents = ((Number) row[3]).longValue();
            TxBalanceAccumulator acc = txSums.computeIfAbsent(txId, ignored -> new TxBalanceAccumulator());
            acc.entryCount += count;
            if (direction == EntryDirection.DEBIT) {
                acc.debitCents = Math.addExact(acc.debitCents, sumCents);
            } else {
                acc.creditCents = Math.addExact(acc.creditCents, sumCents);
            }
        }

        int unbalancedTransactionsCount = 0;
        for (LedgerTransaction tx : transactions) {
            TxBalanceAccumulator acc = txSums.getOrDefault(tx.getId(), new TxBalanceAccumulator());
            if (acc.entryCount < 2L || acc.debitCents != acc.creditCents) {
                unbalancedTransactionsCount++;
                long diff = Math.abs(Math.subtractExact(acc.debitCents, acc.creditCents));
                discrepancyCents = Math.addExact(discrepancyCents, diff);
                findings.add("UNBALANCED_TRANSACTION: transactionId=" + tx.getId()
                        + " entryCount=" + acc.entryCount
                        + " debitCents=" + acc.debitCents
                        + " creditCents=" + acc.creditCents);
            }
        }

        // 3. Settled payments gross vs ESCROW DEBIT in PAYMENT_SETTLEMENT
        long totalSettledPaymentsCents = paymentRepository.sumAmountCentsByStatusIn(SETTLED_LIFECYCLE_STATUSES);
        long totalLedgerSettlementsCents = ledgerEntryRepository.sumAmountByReferenceTypeAndAccountTypeAndDirection(
                LedgerReferenceType.PAYMENT_SETTLEMENT,
                AccountType.ESCROW,
                EntryDirection.DEBIT
        );
        if (totalSettledPaymentsCents != totalLedgerSettlementsCents) {
            long diff = Math.abs(Math.subtractExact(totalSettledPaymentsCents, totalLedgerSettlementsCents));
            discrepancyCents = Math.addExact(discrepancyCents, diff);
            findings.add("SETTLEMENT_MISMATCH: settledPaymentsCents=" + totalSettledPaymentsCents
                    + " ledgerSettlementsCents=" + totalLedgerSettlementsCents);
        }

        // 4. Refunds vs ESCROW CREDIT in PAYMENT_REFUND
        long totalRefundsCents = paymentRefundRepository.sumAllRefundedCents();
        long totalLedgerRefundsCents = ledgerEntryRepository.sumAmountByReferenceTypeAndAccountTypeAndDirection(
                LedgerReferenceType.PAYMENT_REFUND,
                AccountType.ESCROW,
                EntryDirection.CREDIT
        );
        if (totalRefundsCents != totalLedgerRefundsCents) {
            long diff = Math.abs(Math.subtractExact(totalRefundsCents, totalLedgerRefundsCents));
            discrepancyCents = Math.addExact(discrepancyCents, diff);
            findings.add("REFUND_MISMATCH: refundsCents=" + totalRefundsCents
                    + " ledgerRefundsCents=" + totalLedgerRefundsCents);
        }

        ReconciliationStatus status = findings.isEmpty()
                ? ReconciliationStatus.BALANCED
                : ReconciliationStatus.DIVERGENT;

        ReconciliationReport report = reportRepository.saveAndFlush(new ReconciliationReport(
                UUID.randomUUID(),
                status,
                accounts.size(),
                divergentAccountsCount,
                transactions.size(),
                unbalancedTransactionsCount,
                totalSettledPaymentsCents,
                totalLedgerSettlementsCents,
                totalRefundsCents,
                totalLedgerRefundsCents,
                discrepancyCents,
                triggerSource,
                serializeFindings(findings),
                effectiveCorrelationId
        ));

        publishSseEvent(report, findings);
        return toResponse(report, findings);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReconciliationReportResponse> listReports(int page, int size) {
        var result = reportRepository.findAllByOrderByCreatedAtDescIdAsc(PageRequest.of(page, size));
        return PageResponse.from(result, report -> toResponse(report, deserializeFindings(report.getFindingsJson())));
    }

    @Transactional(readOnly = true)
    public String exportCsv() {
        List<ReconciliationReport> reports = reportRepository.findAllByOrderByCreatedAtDescIdAsc();
        StringBuilder sb = new StringBuilder(CSV_HEADER).append('\n');
        for (ReconciliationReport report : reports) {
            sb.append(report.getId()).append(',')
                    .append(report.getCreatedAt()).append(',')
                    .append(report.getStatus().name()).append(',')
                    .append(report.getTriggerSource().name()).append(',')
                    .append(report.getCheckedAccountsCount()).append(',')
                    .append(report.getDivergentAccountsCount()).append(',')
                    .append(report.getCheckedTransactionsCount()).append(',')
                    .append(report.getUnbalancedTransactionsCount()).append(',')
                    .append(report.getTotalSettledPaymentsCents()).append(',')
                    .append(report.getTotalLedgerSettlementsCents()).append(',')
                    .append(report.getTotalRefundsCents()).append(',')
                    .append(report.getTotalLedgerRefundsCents()).append(',')
                    .append(report.getDiscrepancyCents())
                    .append('\n');
        }
        return sb.toString();
    }

    private void publishSseEvent(ReconciliationReport report, List<String> findings) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", report.getId());
        payload.put("status", report.getStatus().name());
        payload.put("checkedAccountsCount", report.getCheckedAccountsCount());
        payload.put("divergentAccountsCount", report.getDivergentAccountsCount());
        payload.put("checkedTransactionsCount", report.getCheckedTransactionsCount());
        payload.put("unbalancedTransactionsCount", report.getUnbalancedTransactionsCount());
        payload.put("discrepancyCents", report.getDiscrepancyCents());
        payload.put("triggerSource", report.getTriggerSource().name());
        payload.put("findingsCount", findings.size());

        OutboxEventEnvelope envelope = new OutboxEventEnvelope(
                UUID.randomUUID(),
                "reconciliation.completed",
                OutboxService.SCHEMA_VERSION,
                report.getCreatedAt(),
                report.getCorrelationId(),
                null,
                payload
        );
        eventPublisher.publishEvent(new DomainStreamEvent(envelope));
    }

    private ReconciliationReportResponse toResponse(ReconciliationReport report, List<String> findings) {
        return new ReconciliationReportResponse(
                report.getId(),
                report.getStatus(),
                report.getCheckedAccountsCount(),
                report.getDivergentAccountsCount(),
                report.getCheckedTransactionsCount(),
                report.getUnbalancedTransactionsCount(),
                report.getTotalSettledPaymentsCents(),
                report.getTotalLedgerSettlementsCents(),
                report.getTotalRefundsCents(),
                report.getTotalLedgerRefundsCents(),
                report.getDiscrepancyCents(),
                report.getTriggerSource(),
                findings,
                report.getCorrelationId(),
                report.getCreatedAt()
        );
    }

    private String serializeFindings(List<String> findings) {
        try {
            return objectMapper.writeValueAsString(findings);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar findings de conciliação.", ex);
        }
    }

    private List<String> deserializeFindings(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao desserializar findings de conciliação.", ex);
        }
    }

    private static final class TxBalanceAccumulator {
        long entryCount;
        long debitCents;
        long creditCents;
    }
}
