package dev.ledgerpulse.core.ledger.repository;

import dev.ledgerpulse.core.ledger.domain.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface LedgerTransactionRepository
        extends JpaRepository<LedgerTransaction, UUID>, JpaSpecificationExecutor<LedgerTransaction> {
}
