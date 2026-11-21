package dev.ledgerpulse.core.ledger.service;

import dev.ledgerpulse.core.ledger.api.LedgerEntryResponse;
import dev.ledgerpulse.core.ledger.api.LedgerTransactionDetailResponse;
import dev.ledgerpulse.core.ledger.api.LedgerTransactionSummaryResponse;
import dev.ledgerpulse.core.ledger.domain.Account;
import dev.ledgerpulse.core.ledger.domain.AccountType;
import dev.ledgerpulse.core.ledger.domain.EntryDirection;
import dev.ledgerpulse.core.ledger.domain.LedgerEntry;
import dev.ledgerpulse.core.ledger.domain.LedgerReferenceType;
import dev.ledgerpulse.core.ledger.domain.LedgerTransaction;
import dev.ledgerpulse.core.ledger.repository.AccountRepository;
import dev.ledgerpulse.core.ledger.repository.LedgerEntryRepository;
import dev.ledgerpulse.core.ledger.repository.LedgerTransactionRepository;
import dev.ledgerpulse.core.web.DomainValidationException;
import dev.ledgerpulse.core.web.PageResponse;
import dev.ledgerpulse.core.web.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(
            AccountRepository accountRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository
    ) {
        this.accountRepository = accountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Atomically posts a balanced double-entry transaction and updates account balances with @Version optimistic locking.
     */
    @Transactional
    public LedgerTransaction postTransaction(LedgerPostingCommand command) {
        validateCommand(command);

        List<UUID> sortedAccountIds = command.legs().stream()
                .map(LedgerPostingCommand.LedgerLeg::accountId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        // Load accounts in deterministic UUID order to prevent database deadlocks.
        Map<UUID, Account> accountsById = new LinkedHashMap<>();
        for (UUID accountId : sortedAccountIds) {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada: " + accountId));
            if (!command.currency().equals(account.getCurrency())) {
                throw new DomainValidationException("Moeda incompatível na conta " + accountId);
            }
            accountsById.put(accountId, account);
        }

        LedgerTransaction transaction = ledgerTransactionRepository.save(new LedgerTransaction(
                UUID.randomUUID(),
                command.referenceType(),
                command.referenceId(),
                command.description(),
                command.currency(),
                command.correlationId()
        ));

        List<LedgerEntry> entries = new ArrayList<>(command.legs().size());
        for (LedgerPostingCommand.LedgerLeg leg : command.legs()) {
            Account account = accountsById.get(leg.accountId());
            long balanceAfter = account.applyEntry(leg.direction(), leg.amountCents());
            entries.add(new LedgerEntry(
                    UUID.randomUUID(),
                    transaction.getId(),
                    account.getId(),
                    leg.direction(),
                    leg.amountCents(),
                    balanceAfter
            ));
        }

        // Flush updated accounts in deterministic order so @Version conflicts surface immediately.
        accountRepository.saveAllAndFlush(accountsById.values());
        ledgerEntryRepository.saveAll(entries);
        return transaction;
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerTransactionSummaryResponse> listTransactions(
            UUID accountId,
            LedgerReferenceType referenceType,
            UUID referenceId,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        Specification<LedgerTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (accountId != null) {
                Subquery<UUID> subquery = query.subquery(UUID.class);
                Root<LedgerEntry> entryRoot = subquery.from(LedgerEntry.class);
                subquery.select(entryRoot.get("ledgerTransactionId"))
                        .where(cb.equal(entryRoot.get("accountId"), accountId));
                predicates.add(root.get("id").in(subquery));
            }
            if (referenceType != null) {
                predicates.add(cb.equal(root.get("referenceType"), referenceType));
            }
            if (referenceId != null) {
                predicates.add(cb.equal(root.get("referenceId"), referenceId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        Page<LedgerTransaction> result = ledgerTransactionRepository.findAll(spec, pageable);

        List<UUID> txIds = result.getContent().stream().map(LedgerTransaction::getId).toList();
        Map<UUID, List<LedgerEntry>> entriesByTxId = txIds.isEmpty()
                ? Map.of()
                : ledgerEntryRepository.findByLedgerTransactionIdInOrderByDirectionDescCreatedAtAscIdAsc(txIds).stream()
                        .collect(Collectors.groupingBy(LedgerEntry::getLedgerTransactionId));

        return PageResponse.from(result, tx -> toSummary(tx, entriesByTxId.getOrDefault(tx.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public LedgerTransactionDetailResponse getTransactionById(UUID id) {
        LedgerTransaction tx = ledgerTransactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transação contábil não encontrada: " + id));

        List<LedgerEntry> entries = ledgerEntryRepository.findByLedgerTransactionIdOrderByDirectionDescCreatedAtAscIdAsc(id)
                .stream()
                .sorted(Comparator
                        .comparingInt((LedgerEntry entry) -> entry.getDirection() == EntryDirection.DEBIT ? 0 : 1)
                        .thenComparing(LedgerEntry::getCreatedAt)
                        .thenComparing(LedgerEntry::getId))
                .toList();
        Set<UUID> accountIds = entries.stream().map(LedgerEntry::getAccountId).collect(Collectors.toSet());
        Map<UUID, AccountType> accountTypes = accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Account::getAccountType));

        long totalDebit = sumByDirection(entries, EntryDirection.DEBIT);
        long totalCredit = sumByDirection(entries, EntryDirection.CREDIT);
        boolean balanced = totalDebit == totalCredit && totalDebit > 0;

        List<LedgerEntryResponse> entryResponses = entries.stream()
                .map(entry -> LedgerEntryResponse.from(entry, accountTypes.get(entry.getAccountId())))
                .toList();

        return new LedgerTransactionDetailResponse(
                tx.getId(),
                tx.getReferenceType(),
                tx.getReferenceId(),
                tx.getDescription(),
                tx.getCurrency(),
                totalDebit,
                totalCredit,
                balanced,
                tx.getCorrelationId(),
                tx.getCreatedAt(),
                entryResponses
        );
    }

    private void validateCommand(LedgerPostingCommand command) {
        if (command == null || command.referenceType() == null || command.referenceId() == null) {
            throw new DomainValidationException("Referência da transação contábil é obrigatória.");
        }
        if (command.legs() == null || command.legs().size() < 2) {
            throw new DomainValidationException("Transação contábil exige no mínimo 2 pernas (DEBIT e CREDIT).");
        }

        long totalDebit = 0L;
        long totalCredit = 0L;
        for (LedgerPostingCommand.LedgerLeg leg : command.legs()) {
            if (leg.accountId() == null || leg.direction() == null) {
                throw new DomainValidationException("Conta e direção são obrigatórias em todas as pernas.");
            }
            if (leg.amountCents() <= 0) {
                throw new DomainValidationException("Todas as pernas do Ledger exigem amountCents > 0.");
            }
            if (leg.direction() == EntryDirection.DEBIT) {
                totalDebit = Math.addExact(totalDebit, leg.amountCents());
            } else {
                totalCredit = Math.addExact(totalCredit, leg.amountCents());
            }
        }

        if (totalDebit == 0 || totalDebit != totalCredit) {
            throw new DomainValidationException(
                    "Transação contábil desequilibrada: total DEBIT (" + totalDebit + ") != total CREDIT (" + totalCredit + ")."
            );
        }
    }

    private LedgerTransactionSummaryResponse toSummary(LedgerTransaction tx, List<LedgerEntry> entries) {
        long totalDebit = sumByDirection(entries, EntryDirection.DEBIT);
        long totalCredit = sumByDirection(entries, EntryDirection.CREDIT);
        return new LedgerTransactionSummaryResponse(
                tx.getId(),
                tx.getReferenceType(),
                tx.getReferenceId(),
                tx.getDescription(),
                tx.getCurrency(),
                totalDebit,
                totalCredit,
                totalDebit == totalCredit && totalDebit > 0,
                tx.getCorrelationId(),
                tx.getCreatedAt()
        );
    }

    private long sumByDirection(List<LedgerEntry> entries, EntryDirection direction) {
        return entries.stream()
                .filter(entry -> entry.getDirection() == direction)
                .mapToLong(LedgerEntry::getAmountCents)
                .reduce(0L, Math::addExact);
    }
}
