package dev.ledgerpulse.core.refund.service;

import dev.ledgerpulse.core.payment.domain.PaymentSplit;
import dev.ledgerpulse.core.refund.api.RecipientReversalDto;
import dev.ledgerpulse.core.web.DomainValidationException;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes exact, bounded cumulative pro-rata reversals across platform fee, merchant net,
 * and split recipients using BigInteger/long arithmetic (zero float/double usage).
 */
@Component
public class RefundCalculator {

    public record PriorRefundTotals(
            long alreadyRefundedCents,
            long alreadyFeeReversedCents,
            long alreadyMerchantReversedCents,
            Map<UUID, Long> alreadyRecipientReversedCents
    ) {
    }

    public record RefundBreakdown(
            long refundAmountCents,
            long platformFeeReversedCents,
            long netMerchantReversedCents,
            List<RecipientReversalDto> recipientReversals,
            long totalRefundedAfterCents,
            long remainingRefundableAfterCents
    ) {
    }

    public RefundBreakdown calculate(
            long originalGrossCents,
            long originalFeeCents,
            long originalNetMerchantCents,
            List<PaymentSplit> orderedSplits,
            PriorRefundTotals priorTotals,
            long refundAmountCents
    ) {
        long remainingGross = Math.subtractExact(originalGrossCents, priorTotals.alreadyRefundedCents());
        if (refundAmountCents <= 0 || refundAmountCents > remainingGross) {
            throw new DomainValidationException(
                    "amountCents inválido (" + refundAmountCents + "); saldo máximo estornável é " + remainingGross + "."
            );
        }

        long cumulativeGrossAfter = Math.addExact(priorTotals.alreadyRefundedCents(), refundAmountCents);
        BigInteger grossBig = BigInteger.valueOf(originalGrossCents);
        BigInteger cumAfterBig = BigInteger.valueOf(cumulativeGrossAfter);

        int splitCount = orderedSplits == null ? 0 : orderedSplits.size();
        // Bucket order: 0 = MERCHANT, 1 = PLATFORM_FEE, 2..N+1 = RECIPIENT splits
        int bucketCount = 2 + splitCount;
        long[] original = new long[bucketCount];
        long[] prior = new long[bucketCount];
        long[] capacity = new long[bucketCount];
        long[] step = new long[bucketCount];

        original[0] = originalNetMerchantCents;
        prior[0] = priorTotals.alreadyMerchantReversedCents();
        capacity[0] = Math.max(0L, original[0] - prior[0]);

        original[1] = originalFeeCents;
        prior[1] = priorTotals.alreadyFeeReversedCents();
        capacity[1] = Math.max(0L, original[1] - prior[1]);

        for (int i = 0; i < splitCount; i++) {
            PaymentSplit split = orderedSplits.get(i);
            int idx = 2 + i;
            original[idx] = split.getAmountCents();
            prior[idx] = priorTotals.alreadyRecipientReversedCents().getOrDefault(split.getRecipientAccountId(), 0L);
            capacity[idx] = Math.max(0L, original[idx] - prior[idx]);
        }

        long allocated = 0L;
        for (int i = 0; i < bucketCount; i++) {
            long targetCum = BigInteger.valueOf(original[i])
                    .multiply(cumAfterBig)
                    .divide(grossBig)
                    .longValueExact();
            long rawStep = Math.max(0L, targetCum - prior[i]);
            step[i] = Math.min(capacity[i], rawStep);
            allocated = Math.addExact(allocated, step[i]);
        }

        if (allocated < refundAmountCents) {
            long remainder = refundAmountCents - allocated;
            for (int i = 0; i < bucketCount && remainder > 0L; i++) {
                long headroom = capacity[i] - step[i];
                if (headroom > 0L) {
                    long take = Math.min(remainder, headroom);
                    step[i] += take;
                    remainder -= take;
                }
            }
        } else if (allocated > refundAmountCents) {
            long excess = allocated - refundAmountCents;
            for (int i = 0; i < bucketCount && excess > 0L; i++) {
                if (step[i] > 0L) {
                    long trim = Math.min(excess, step[i]);
                    step[i] -= trim;
                    excess -= trim;
                }
            }
        }

        List<RecipientReversalDto> recipientReversals = new ArrayList<>(splitCount);
        for (int i = 0; i < splitCount; i++) {
            recipientReversals.add(new RecipientReversalDto(
                    orderedSplits.get(i).getRecipientAccountId(),
                    step[2 + i]
            ));
        }

        long remainingAfter = Math.subtractExact(originalGrossCents, cumulativeGrossAfter);
        return new RefundBreakdown(
                refundAmountCents,
                step[1],
                step[0],
                recipientReversals,
                cumulativeGrossAfter,
                remainingAfter
        );
    }

    public static Map<UUID, Long> emptyRecipientMap() {
        return new LinkedHashMap<>();
    }
}
