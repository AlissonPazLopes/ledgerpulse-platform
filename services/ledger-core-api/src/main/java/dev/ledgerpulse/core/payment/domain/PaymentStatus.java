package dev.ledgerpulse.core.payment.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum PaymentStatus {
    CREATED,
    RISK_ANALYSIS,
    AUTHORIZED,
    MANUAL_REVIEW,
    SETTLED,
    DECLINED,
    PARTIALLY_REFUNDED,
    REFUNDED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = buildTransitions();

    private static Map<PaymentStatus, Set<PaymentStatus>> buildTransitions() {
        Map<PaymentStatus, Set<PaymentStatus>> map = new EnumMap<>(PaymentStatus.class);
        map.put(CREATED, EnumSet.of(RISK_ANALYSIS));
        map.put(RISK_ANALYSIS, EnumSet.of(AUTHORIZED, MANUAL_REVIEW, DECLINED));
        map.put(MANUAL_REVIEW, EnumSet.of(AUTHORIZED, DECLINED));
        map.put(AUTHORIZED, EnumSet.of(SETTLED));
        map.put(SETTLED, EnumSet.of(PARTIALLY_REFUNDED, REFUNDED));
        map.put(PARTIALLY_REFUNDED, EnumSet.of(PARTIALLY_REFUNDED, REFUNDED));
        map.put(DECLINED, EnumSet.noneOf(PaymentStatus.class));
        map.put(REFUNDED, EnumSet.noneOf(PaymentStatus.class));
        return map;
    }

    public boolean canTransitionTo(PaymentStatus next) {
        return next != null && ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }
}
