package dev.ledgerpulse.core.ledger.repository;

import dev.ledgerpulse.core.ledger.domain.Account;
import dev.ledgerpulse.core.ledger.domain.AccountStatus;
import dev.ledgerpulse.core.ledger.domain.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {

    Optional<Account> findFirstByAccountTypeAndStatusOrderByCreatedAtAsc(
            AccountType accountType,
            AccountStatus status
    );

    Optional<Account> findFirstByMerchantIdAndAccountTypeAndStatusOrderByCreatedAtAsc(
            UUID merchantId,
            AccountType accountType,
            AccountStatus status
    );
}
