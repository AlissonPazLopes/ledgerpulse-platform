package dev.ledgerpulse.core.payment;

import dev.ledgerpulse.core.ledger.domain.Account;
import dev.ledgerpulse.core.ledger.domain.AccountStatus;
import dev.ledgerpulse.core.ledger.domain.AccountType;
import dev.ledgerpulse.core.payment.api.PaymentSplitRequest;
import dev.ledgerpulse.core.payment.domain.PaymentStatus;
import dev.ledgerpulse.core.payment.domain.SplitType;
import dev.ledgerpulse.core.payment.service.SplitCalculator;
import dev.ledgerpulse.core.web.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitCalculatorTest {

    private final SplitCalculator calculator = new SplitCalculator();

    @Test
    void shouldCalculateIntegerCentsPercentageAndFixedSplitsAccurately() {
        UUID recipientA = UUID.randomUUID();
        UUID recipientB = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        Map<UUID, Account> accounts = Map.of(
                recipientA, new Account(recipientA, merchantId, AccountType.RECIPIENT, "Seller A", "BRL", AccountStatus.ACTIVE),
                recipientB, new Account(recipientB, merchantId, AccountType.RECIPIENT, "Seller B", "BRL", AccountStatus.ACTIVE)
        );

        var result = calculator.calculate(
                15_000L,
                250,
                "BRL",
                List.of(
                        new PaymentSplitRequest(recipientA, SplitType.PERCENTAGE, 2000, null),
                        new PaymentSplitRequest(recipientB, SplitType.FIXED, null, 1_000L)
                ),
                accounts
        );

        assertThat(result.platformFeeCents()).isEqualTo(375L);
        assertThat(result.splits()).hasSize(2);
        assertThat(result.splits().get(0).amountCents()).isEqualTo(3_000L);
        assertThat(result.splits().get(1).amountCents()).isEqualTo(1_000L);
        assertThat(result.netMerchantCents()).isEqualTo(10_625L);
        assertThat(result.platformFeeCents() + 3_000L + 1_000L + result.netMerchantCents()).isEqualTo(15_000L);
    }

    @Test
    void shouldRejectExceedingSplitsDuplicateRecipientsAndInvalidAccountType() {
        UUID recipientId = UUID.randomUUID();
        UUID merchantAccId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        Map<UUID, Account> accounts = Map.of(
                recipientId, new Account(recipientId, merchantId, AccountType.RECIPIENT, "Seller A", "BRL", AccountStatus.ACTIVE),
                merchantAccId, new Account(merchantAccId, merchantId, AccountType.MERCHANT, "Merchant Main", "BRL", AccountStatus.ACTIVE)
        );

        assertThatThrownBy(() -> calculator.calculate(
                10_000L,
                250,
                "BRL",
                List.of(new PaymentSplitRequest(recipientId, SplitType.FIXED, null, 9_800L)),
                accounts
        )).isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("excede");

        assertThatThrownBy(() -> calculator.calculate(
                10_000L,
                250,
                "BRL",
                List.of(
                        new PaymentSplitRequest(recipientId, SplitType.FIXED, null, 1_000L),
                        new PaymentSplitRequest(recipientId, SplitType.FIXED, null, 1_000L)
                ),
                accounts
        )).isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("duplicada");

        assertThatThrownBy(() -> calculator.calculate(
                10_000L,
                250,
                "BRL",
                List.of(new PaymentSplitRequest(merchantAccId, SplitType.FIXED, null, 1_000L)),
                accounts
        )).isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("RECIPIENT");
    }

    @Test
    void shouldValidatePaymentStateMachineTransitions() {
        assertThat(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.RISK_ANALYSIS)).isTrue();
        assertThat(PaymentStatus.RISK_ANALYSIS.canTransitionTo(PaymentStatus.AUTHORIZED)).isTrue();
        assertThat(PaymentStatus.RISK_ANALYSIS.canTransitionTo(PaymentStatus.MANUAL_REVIEW)).isTrue();
        assertThat(PaymentStatus.RISK_ANALYSIS.canTransitionTo(PaymentStatus.DECLINED)).isTrue();
        assertThat(PaymentStatus.AUTHORIZED.canTransitionTo(PaymentStatus.SETTLED)).isTrue();
        assertThat(PaymentStatus.SETTLED.canTransitionTo(PaymentStatus.PARTIALLY_REFUNDED)).isTrue();
        assertThat(PaymentStatus.SETTLED.canTransitionTo(PaymentStatus.REFUNDED)).isTrue();
        assertThat(PaymentStatus.DECLINED.canTransitionTo(PaymentStatus.SETTLED)).isFalse();
        assertThat(PaymentStatus.REFUNDED.canTransitionTo(PaymentStatus.SETTLED)).isFalse();
    }
}
