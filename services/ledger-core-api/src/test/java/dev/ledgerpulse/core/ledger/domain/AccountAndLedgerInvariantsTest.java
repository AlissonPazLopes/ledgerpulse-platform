package dev.ledgerpulse.core.ledger.domain;

import dev.ledgerpulse.core.web.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountAndLedgerInvariantsTest {

    @Test
    void shouldCreditAndDebitMerchantAccountWithinPositiveBalance() {
        Account merchant = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AccountType.MERCHANT,
                "Acme Main",
                "BRL",
                AccountStatus.ACTIVE
        );

        long afterCredit = merchant.applyEntry(EntryDirection.CREDIT, 15_000L);
        assertThat(afterCredit).isEqualTo(15_000L);
        assertThat(merchant.getAvailableBalanceCents()).isEqualTo(15_000L);

        long afterDebit = merchant.applyEntry(EntryDirection.DEBIT, 5_000L);
        assertThat(afterDebit).isEqualTo(10_000L);
        assertThat(merchant.getAvailableBalanceCents()).isEqualTo(10_000L);
    }

    @Test
    void shouldRejectDebitExceedingBalanceForNonEscrowAccount() {
        Account recipient = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AccountType.RECIPIENT,
                "Partner Alpha",
                "BRL",
                AccountStatus.ACTIVE
        );
        recipient.applyEntry(EntryDirection.CREDIT, 1_000L);

        assertThatThrownBy(() -> recipient.applyEntry(EntryDirection.DEBIT, 1_001L))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Saldo insuficiente");
    }

    @Test
    void shouldAllowNegativeBalanceOnlyForEscrowAccount() {
        Account escrow = new Account(
                UUID.randomUUID(),
                null,
                AccountType.ESCROW,
                "Clearing Escrow",
                "BRL",
                AccountStatus.ACTIVE
        );

        long afterDebit = escrow.applyEntry(EntryDirection.DEBIT, 15_000L);
        assertThat(afterDebit).isEqualTo(-15_000L);
        assertThat(escrow.getAvailableBalanceCents()).isEqualTo(-15_000L);
    }

    @Test
    void shouldRejectZeroNegativeAmountAndBlockedAccount() {
        Account merchant = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AccountType.MERCHANT,
                "Acme Main",
                "BRL",
                AccountStatus.ACTIVE
        );

        assertThatThrownBy(() -> merchant.applyEntry(EntryDirection.CREDIT, 0L))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> merchant.applyEntry(EntryDirection.CREDIT, -100L))
                .isInstanceOf(DomainValidationException.class);

        Account blocked = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AccountType.MERCHANT,
                "Blocked Account",
                "BRL",
                AccountStatus.BLOCKED
        );

        assertThatThrownBy(() -> blocked.applyEntry(EntryDirection.CREDIT, 1_000L))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("bloqueada");
    }

    @Test
    void shouldRejectInvalidAccountConstructionAndZeroLedgerEntry() {
        assertThatThrownBy(() -> new Account(
                UUID.randomUUID(),
                null,
                AccountType.MERCHANT,
                "Missing Merchant",
                "BRL",
                AccountStatus.ACTIVE
        )).isInstanceOf(DomainValidationException.class);

        assertThatThrownBy(() -> new Account(
                UUID.randomUUID(),
                null,
                AccountType.ESCROW,
                "Wrong Currency",
                "USD",
                AccountStatus.ACTIVE
        )).isInstanceOf(DomainValidationException.class);

        assertThatThrownBy(() -> new LedgerEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntryDirection.CREDIT,
                0L,
                0L
        )).isInstanceOf(DomainValidationException.class);
    }
}
