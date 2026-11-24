package dev.ledgerpulse.core.ledger.repository;

import dev.ledgerpulse.core.ledger.domain.AccountType;
import dev.ledgerpulse.core.ledger.domain.EntryDirection;
import dev.ledgerpulse.core.ledger.domain.LedgerEntry;
import dev.ledgerpulse.core.ledger.domain.LedgerReferenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByLedgerTransactionIdOrderByDirectionDescCreatedAtAscIdAsc(UUID ledgerTransactionId);

    List<LedgerEntry> findByLedgerTransactionIdInOrderByDirectionDescCreatedAtAscIdAsc(Collection<UUID> ledgerTransactionIds);

    @Query("""
            SELECT e.accountId, e.direction, COALESCE(SUM(e.amountCents), 0)
            FROM LedgerEntry e
            GROUP BY e.accountId, e.direction
            """)
    List<Object[]> summarizeByAccountAndDirection();

    @Query("""
            SELECT e.ledgerTransactionId, e.direction, COUNT(e), COALESCE(SUM(e.amountCents), 0)
            FROM LedgerEntry e
            GROUP BY e.ledgerTransactionId, e.direction
            """)
    List<Object[]> summarizeByTransactionAndDirection();

    @Query("""
            SELECT COALESCE(SUM(e.amountCents), 0)
            FROM LedgerEntry e
            JOIN LedgerTransaction t ON e.ledgerTransactionId = t.id
            JOIN Account a ON e.accountId = a.id
            WHERE t.referenceType = :referenceType
              AND a.accountType = :accountType
              AND e.direction = :direction
            """)
    long sumAmountByReferenceTypeAndAccountTypeAndDirection(
            @Param("referenceType") LedgerReferenceType referenceType,
            @Param("accountType") AccountType accountType,
            @Param("direction") EntryDirection direction
    );
}
