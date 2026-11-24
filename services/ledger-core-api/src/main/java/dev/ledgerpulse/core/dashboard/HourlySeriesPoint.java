package dev.ledgerpulse.core.dashboard;

public record HourlySeriesPoint(
        String hourLabel,
        long volumeCents,
        long paymentCount,
        int averageRiskScore
) {
}
