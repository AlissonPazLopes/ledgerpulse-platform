package dev.ledgerpulse.core.ledger.service;

import dev.ledgerpulse.core.auth.repository.MerchantRepository;
import dev.ledgerpulse.core.ledger.api.AccountResponse;
import dev.ledgerpulse.core.ledger.api.CreateAccountRequest;
import dev.ledgerpulse.core.ledger.domain.Account;
import dev.ledgerpulse.core.ledger.domain.AccountStatus;
import dev.ledgerpulse.core.ledger.domain.AccountType;
import dev.ledgerpulse.core.ledger.repository.AccountRepository;
import dev.ledgerpulse.core.web.DomainValidationException;
import dev.ledgerpulse.core.web.PageResponse;
import dev.ledgerpulse.core.web.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final MerchantRepository merchantRepository;

    public AccountService(AccountRepository accountRepository, MerchantRepository merchantRepository) {
        this.accountRepository = accountRepository;
        this.merchantRepository = merchantRepository;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        if ((request.accountType() == AccountType.MERCHANT || request.accountType() == AccountType.RECIPIENT)
                && request.merchantId() == null) {
            throw new DomainValidationException("merchantId é obrigatório para contas MERCHANT e RECIPIENT.");
        }
        if (request.merchantId() != null && !merchantRepository.existsById(request.merchantId())) {
            throw new ResourceNotFoundException("Merchant não encontrado: " + request.merchantId());
        }

        Account account = new Account(
                UUID.randomUUID(),
                request.merchantId(),
                request.accountType(),
                request.holderName().trim(),
                request.currency(),
                AccountStatus.ACTIVE
        );
        Account saved = accountRepository.saveAndFlush(account);
        return AccountResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> listAccounts(
            AccountType accountType,
            UUID merchantId,
            int page,
            int size
    ) {
        Specification<Account> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (accountType != null) {
                predicates.add(cb.equal(root.get("accountType"), accountType));
            }
            if (merchantId != null) {
                predicates.add(cb.equal(root.get("merchantId"), merchantId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        return PageResponse.from(accountRepository.findAll(spec, pageable), AccountResponse::from);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID id) {
        return accountRepository.findById(id)
                .map(AccountResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada: " + id));
    }
}
