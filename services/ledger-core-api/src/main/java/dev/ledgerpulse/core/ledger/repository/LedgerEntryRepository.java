package dev.ledgerpulse.core.ledger.repository;

import dev.ledgerpulse.core.ledger.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByLedgerTransactionIdOrderByDirectionDescCreatedAtAscIdAsc(UUID ledgerTransactionId);

    List<LedgerEntry> findByLedgerTransactionIdInOrderByDirectionDescCreatedAtAscIdAsc(Collection<UUID> ledgerTransactionIds);
}
