package dev.ledgerpulse.core.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        String store,
        int capacity,
        int refillTokens,
        long refillPeriodSeconds
) {
    public RateLimitProperties {
        if (store == null || store.isBlank()) {
            store = "redis";
        }
        if (capacity <= 0) {
            capacity = 60;
        }
        if (refillTokens <= 0) {
            refillTokens = capacity;
        }
        if (refillPeriodSeconds <= 0) {
            refillPeriodSeconds = 60L;
        }
    }
}
