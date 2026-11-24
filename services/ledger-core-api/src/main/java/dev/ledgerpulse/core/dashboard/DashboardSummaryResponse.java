package dev.ledgerpulse.core.dashboard;

import java.time.Instant;
import java.util.List;

public record DashboardSummaryResponse(
        long tpvCents,
        long totalPayments,
        long settledCount,
        long manualReviewCount,
        long declinedCount,
        long refundedCount,
        long totalRefundedCents,
        int approvalRateBps,
        int averageRiskScore,
        List<HourlySeriesPoint> hourlySeries,
        Instant generatedAt
) {
}
