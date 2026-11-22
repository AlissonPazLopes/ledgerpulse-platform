package dev.ledgerpulse.core.ledger;

import dev.ledgerpulse.core.auth.service.DevIdentitySeeder;
import dev.ledgerpulse.core.ledger.domain.Account;
import dev.ledgerpulse.core.ledger.domain.AccountStatus;
import dev.ledgerpulse.core.ledger.domain.AccountType;
import dev.ledgerpulse.core.ledger.domain.EntryDirection;
import dev.ledgerpulse.core.ledger.domain.LedgerReferenceType;
import dev.ledgerpulse.core.ledger.repository.AccountRepository;
import dev.ledgerpulse.core.ledger.service.LedgerPostingCommand;
import dev.ledgerpulse.core.ledger.service.LedgerService;
import dev.ledgerpulse.core.web.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class LedgerServiceConcurrencyTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void shouldRejectUnbalancedDoubleEntryTransaction() {
        Account escrow = createTestAccount(null, AccountType.ESCROW, "Test Escrow Unbalanced");
        Account merchant = createTestAccount(DevIdentitySeeder.DEMO_MERCHANT_ID, AccountType.MERCHANT, "Test Merchant Unbalanced");

        LedgerPostingCommand unbalanced = new LedgerPostingCommand(
                LedgerReferenceType.PAYMENT_SETTLEMENT,
                UUID.randomUUID(),
                "Unbalanced tx",
                "BRL",
                "corr-unbalanced",
                List.of(
                        new LedgerPostingCommand.LedgerLeg(escrow.getId(), EntryDirection.DEBIT, 1_000L),
                        new LedgerPostingCommand.LedgerLeg(merchant.getId(), EntryDirection.CREDIT, 999L)
                )
        );

        assertThatThrownBy(() -> ledgerService.postTransaction(unbalanced))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("desequilibrada");

        assertThat(accountRepository.findById(merchant.getId()).orElseThrow().getAvailableBalanceCents()).isZero();
    }

    @Test
    void shouldThrowOptimisticLockingFailureWhenConcurrentTransactionsModifySameAccountVersion() throws Exception {
        Account targetMerchant = createTestAccount(
                DevIdentitySeeder.DEMO_MERCHANT_ID,
                AccountType.MERCHANT,
                "Deterministic Optimistic Lock Merchant"
        );
        Account escrowOne = createTestAccount(null, AccountType.ESCROW, "Deterministic Escrow 1");
        Account escrowTwo = createTestAccount(null, AccountType.ESCROW, "Deterministic Escrow 2");

        CountDownLatch tx1Loaded = new CountDownLatch(1);
        CountDownLatch tx2Committed = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Throwable> slowTxFuture = pool.submit(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        Account loaded = accountRepository.findById(targetMerchant.getId()).orElseThrow();
                        tx1Loaded.countDown();
                        awaitLatch(tx2Committed);
                        loaded.applyEntry(EntryDirection.CREDIT, 2_500L);
                        accountRepository.saveAndFlush(loaded);
                    });
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });

            assertThat(tx1Loaded.await(5, TimeUnit.SECONDS)).isTrue();

            ledgerService.postTransaction(new LedgerPostingCommand(
                    LedgerReferenceType.PAYMENT_SETTLEMENT,
                    UUID.randomUUID(),
                    "Fast concurrent tx",
                    "BRL",
                    "corr-fast",
                    List.of(
                            new LedgerPostingCommand.LedgerLeg(escrowOne.getId(), EntryDirection.DEBIT, 1_000L),
                            new LedgerPostingCommand.LedgerLeg(targetMerchant.getId(), EntryDirection.CREDIT, 1_000L)
                    )
            ));
            tx2Committed.countDown();

            Throwable failure = slowTxFuture.get(10, TimeUnit.SECONDS);
            assertThat(failure).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        }

        Account reloaded = accountRepository.findById(targetMerchant.getId()).orElseThrow();
        assertThat(reloaded.getAvailableBalanceCents()).isEqualTo(1_000L);
        assertThat(reloaded.getVersion()).isEqualTo(1L);

        // Ensure second escrow stays untouched after rollback
        assertThat(accountRepository.findById(escrowTwo.getId()).orElseThrow().getAvailableBalanceCents()).isZero();
    }

    @Test
    void shouldMaintainExactBalanceWithoutLostUpdatesUnderMultiThreadContention() throws Exception {
        int threads = 8;
        long amountPerPost = 1_500L;

        Account contendedMerchant = createTestAccount(
                DevIdentitySeeder.DEMO_MERCHANT_ID,
                AccountType.MERCHANT,
                "MultiThread Contention Merchant"
        );
        List<Account> threadEscrows = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            threadEscrows.add(createTestAccount(null, AccountType.ESCROW, "Thread Escrow " + i));
        }

        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger optimisticConflicts = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final Account escrow = threadEscrows.get(i);
                tasks.add(() -> {
                    startGate.await(5, TimeUnit.SECONDS);
                    try {
                        ledgerService.postTransaction(new LedgerPostingCommand(
                                LedgerReferenceType.PAYMENT_SETTLEMENT,
                                UUID.randomUUID(),
                                "Concurrent credit",
                                "BRL",
                                "corr-mt-" + escrow.getId(),
                                List.of(
                                        new LedgerPostingCommand.LedgerLeg(escrow.getId(), EntryDirection.DEBIT, amountPerPost),
                                        new LedgerPostingCommand.LedgerLeg(contendedMerchant.getId(), EntryDirection.CREDIT, amountPerPost)
                                )
                        ));
                        successes.incrementAndGet();
                    } catch (ObjectOptimisticLockingFailureException ex) {
                        optimisticConflicts.incrementAndGet();
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = tasks.stream().map(executor::submit).toList();
            startGate.countDown();
            for (Future<Void> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        }

        assertThat(successes.get()).isGreaterThanOrEqualTo(1);
        assertThat(successes.get() + optimisticConflicts.get()).isEqualTo(threads);

        Account finalAccount = accountRepository.findById(contendedMerchant.getId()).orElseThrow();
        assertThat(finalAccount.getAvailableBalanceCents()).isEqualTo(successes.get() * amountPerPost);
        assertThat(finalAccount.getVersion()).isEqualTo(successes.get());
    }

    private Account createTestAccount(UUID merchantId, AccountType type, String holderName) {
        return accountRepository.saveAndFlush(new Account(
                UUID.randomUUID(),
                merchantId,
                type,
                holderName,
                "BRL",
                AccountStatus.ACTIVE
        ));
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent test latch");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
