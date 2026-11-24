package dev.ledgerpulse.core.dashboard;

import dev.ledgerpulse.core.payment.domain.Payment;
import dev.ledgerpulse.core.payment.domain.PaymentStatus;
import dev.ledgerpulse.core.payment.repository.PaymentRepository;
import dev.ledgerpulse.core.refund.repository.PaymentRefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class DashboardService {

    private static final DateTimeFormatter HOUR_LABEL_FORMATTER =
            DateTimeFormatter.ofPattern("HH:00").withZone(ZoneOffset.UTC);

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;

    public DashboardService(
            PaymentRepository paymentRepository,
            PaymentRefundRepository paymentRefundRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentRefundRepository = paymentRefundRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        Map<PaymentStatus, Long> countsByStatus = new EnumMap<>(PaymentStatus.class);
        Map<PaymentStatus, Long> volumesByStatus = new EnumMap<>(PaymentStatus.class);

        long totalPayments = 0L;
        for (Object[] row : paymentRepository.summarizeByStatus()) {
            PaymentStatus status = (PaymentStatus) row[0];
            long count = ((Number) row[1]).longValue();
            long volumeCents = ((Number) row[2]).longValue();
            countsByStatus.put(status, count);
            volumesByStatus.put(status, volumeCents);
            totalPayments = Math.addExact(totalPayments, count);
        }

        long settledCount = countsByStatus.getOrDefault(PaymentStatus.SETTLED, 0L);
        long manualReviewCount = countsByStatus.getOrDefault(PaymentStatus.MANUAL_REVIEW, 0L);
        long declinedCount = countsByStatus.getOrDefault(PaymentStatus.DECLINED, 0L);
        long refundedCount = countsByStatus.getOrDefault(PaymentStatus.PARTIALLY_REFUNDED, 0L)
                + countsByStatus.getOrDefault(PaymentStatus.REFUNDED, 0L);

        long tpvCents = volumesByStatus.getOrDefault(PaymentStatus.SETTLED, 0L)
                + volumesByStatus.getOrDefault(PaymentStatus.PARTIALLY_REFUNDED, 0L)
                + volumesByStatus.getOrDefault(PaymentStatus.REFUNDED, 0L);

        long totalRefundedCents = paymentRefundRepository.sumAllRefundedCents();

        int approvalRateBps = totalPayments == 0L
                ? 0
                : (int) Math.min(10_000L, (settledCount * 10_000L) / totalPayments);

        int averageRiskScore = computeAverageRiskScore();
        Instant now = Instant.now();
        List<HourlySeriesPoint> hourlySeries = computeHourlySeries(now.minus(24, ChronoUnit.HOURS));

        return new DashboardSummaryResponse(
                tpvCents,
                totalPayments,
                settledCount,
                manualReviewCount,
                declinedCount,
                refundedCount,
                totalRefundedCents,
                approvalRateBps,
                averageRiskScore,
                hourlySeries,
                now
        );
    }

    private int computeAverageRiskScore() {
        List<Object[]> rows = paymentRepository.summarizeRiskScores();
        if (rows.isEmpty()) {
            return 0;
        }
        Object[] row = rows.getFirst();
        long sumRisk = ((Number) row[0]).longValue();
        long countRisk = ((Number) row[1]).longValue();
        if (countRisk <= 0L) {
            return 0;
        }
        return (int) (sumRisk / countRisk);
    }

    private List<HourlySeriesPoint> computeHourlySeries(Instant since) {
        List<Payment> recent = paymentRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(since);
        Map<Instant, HourBucket> buckets = new TreeMap<>();
        for (Payment payment : recent) {
            Instant bucketHour = payment.getCreatedAt().truncatedTo(ChronoUnit.HOURS);
            HourBucket bucket = buckets.computeIfAbsent(bucketHour, ignored -> new HourBucket());
            bucket.volumeCents = Math.addExact(bucket.volumeCents, payment.getAmountCents());
            bucket.paymentCount++;
            if (payment.getRiskScore() != null) {
                bucket.riskScoreSum += payment.getRiskScore();
                bucket.riskScoreCount++;
            }
        }

        List<HourlySeriesPoint> series = new ArrayList<>(buckets.size());
        for (Map.Entry<Instant, HourBucket> entry : buckets.entrySet()) {
            HourBucket bucket = entry.getValue();
            int avgRisk = bucket.riskScoreCount == 0L ? 0 : (int) (bucket.riskScoreSum / bucket.riskScoreCount);
            series.add(new HourlySeriesPoint(
                    HOUR_LABEL_FORMATTER.format(entry.getKey()),
                    bucket.volumeCents,
                    bucket.paymentCount,
                    avgRisk
            ));
        }
        return series;
    }

    private static final class HourBucket {
        long volumeCents;
        long paymentCount;
        long riskScoreSum;
        long riskScoreCount;
    }
}
