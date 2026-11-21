package dev.ledgerpulse.core.payment.service;

import dev.ledgerpulse.core.ledger.domain.Account;
import dev.ledgerpulse.core.ledger.domain.AccountType;
import dev.ledgerpulse.core.payment.api.PaymentSplitRequest;
import dev.ledgerpulse.core.payment.domain.SplitType;
import dev.ledgerpulse.core.web.DomainValidationException;
import dev.ledgerpulse.core.web.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure integer-cents split calculator (zero float/double usage).
 */
@Component
public class SplitCalculator {

    private static final long BASIS_POINTS_DIVISOR = 10_000L;

    public record CalculatedSplit(
            UUID recipientAccountId,
            SplitType splitType,
            Integer percentageBps,
            long amountCents
    ) {
    }

    public record SplitCalculationResult(
            long platformFeeCents,
            long netMerchantCents,
            List<CalculatedSplit> splits
    ) {
    }

    public SplitCalculationResult calculate(
            long grossAmountCents,
            int defaultFeeBps,
            String currency,
            List<PaymentSplitRequest> splitRequests,
            Map<UUID, Account> accountsById
    ) {
        if (grossAmountCents <= 0) {
            throw new DomainValidationException("amountCents deve ser maior que zero.");
        }
        if (defaultFeeBps < 0 || defaultFeeBps > BASIS_POINTS_DIVISOR) {
            throw new DomainValidationException("defaultFeeBps inválido: " + defaultFeeBps);
        }

        long platformFeeCents = Math.multiplyExact(grossAmountCents, defaultFeeBps) / BASIS_POINTS_DIVISOR;
        List<PaymentSplitRequest> requests = splitRequests == null ? List.of() : splitRequests;
        List<CalculatedSplit> calculatedSplits = new ArrayList<>(requests.size());
        Set<UUID> seenRecipients = new HashSet<>();
        long sumSplits = 0L;

        for (PaymentSplitRequest item : requests) {
            if (!seenRecipients.add(item.recipientAccountId())) {
                throw new DomainValidationException(
                        "Conta recebedora duplicada nos splits: " + item.recipientAccountId()
                );
            }
            Account recipient = accountsById.get(item.recipientAccountId());
            if (recipient == null) {
                throw new ResourceNotFoundException("Conta recebedora não encontrada: " + item.recipientAccountId());
            }
            if (recipient.getAccountType() != AccountType.RECIPIENT) {
                throw new DomainValidationException(
                        "Conta " + item.recipientAccountId() + " deve ser do tipo RECIPIENT."
                );
            }
            if (!recipient.isActive()) {
                throw new DomainValidationException(
                        "Conta recebedora " + item.recipientAccountId() + " está bloqueada."
                );
            }
            if (!currency.equals(recipient.getCurrency())) {
                throw new DomainValidationException(
                        "Moeda incompatível na conta recebedora " + item.recipientAccountId()
                );
            }

            long splitAmount = computeSplitAmount(grossAmountCents, item);
            sumSplits = Math.addExact(sumSplits, splitAmount);
            calculatedSplits.add(new CalculatedSplit(
                    item.recipientAccountId(),
                    item.splitType(),
                    item.splitType() == SplitType.PERCENTAGE ? item.percentageBps() : null,
                    splitAmount
            ));
        }

        long allocated = Math.addExact(platformFeeCents, sumSplits);
        if (allocated > grossAmountCents) {
            throw new DomainValidationException(
                    "Soma da taxa da plataforma (" + platformFeeCents + ") e splits (" + sumSplits
                            + ") excede o valor bruto (" + grossAmountCents + ")."
            );
        }

        long netMerchantCents = grossAmountCents - allocated;
        return new SplitCalculationResult(platformFeeCents, netMerchantCents, calculatedSplits);
    }

    private long computeSplitAmount(long grossAmountCents, PaymentSplitRequest item) {
        if (item.splitType() == SplitType.PERCENTAGE) {
            if (item.percentageBps() == null || item.percentageBps() < 1 || item.percentageBps() > BASIS_POINTS_DIVISOR) {
                throw new DomainValidationException("percentageBps é obrigatório (1..10000) para split PERCENTAGE.");
            }
            if (item.amountCents() != null) {
                throw new DomainValidationException("amountCents não deve ser informado para split PERCENTAGE.");
            }
            long amount = Math.multiplyExact(grossAmountCents, item.percentageBps()) / BASIS_POINTS_DIVISOR;
            if (amount <= 0) {
                throw new DomainValidationException("Valor calculado do split percentual deve ser maior que zero.");
            }
            return amount;
        }

        if (item.amountCents() == null || item.amountCents() <= 0) {
            throw new DomainValidationException("amountCents é obrigatório (> 0) para split FIXED.");
        }
        if (item.percentageBps() != null) {
            throw new DomainValidationException("percentageBps não deve ser informado para split FIXED.");
        }
        return item.amountCents();
    }
}
