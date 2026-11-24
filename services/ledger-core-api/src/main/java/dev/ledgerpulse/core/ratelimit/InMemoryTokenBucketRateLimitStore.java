package dev.ledgerpulse.core.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "memory")
public class InMemoryTokenBucketRateLimitStore implements RateLimitStore {

    private static final long TOKEN_SCALE = 1_000_000L;

    private static final class BucketState {
        long scaledTokens;
        long lastRefillMs;

        BucketState(long scaledTokens, long lastRefillMs) {
            this.scaledTokens = scaledTokens;
            this.lastRefillMs = lastRefillMs;
        }
    }

    private final ConcurrentHashMap<String, BucketState> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;

    public InMemoryTokenBucketRateLimitStore(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public RateLimitDecision tryConsume(String key) {
        long nowMs = Instant.now().toEpochMilli();
        long capacityScaled = Math.multiplyExact((long) properties.capacity(), TOKEN_SCALE);
        long refillPeriodMs = Math.multiplyExact(properties.refillPeriodSeconds(), 1000L);

        BucketState bucket = buckets.computeIfAbsent(key, ignored -> new BucketState(capacityScaled, nowMs));
        synchronized (bucket) {
            long elapsedMs = Math.max(0L, nowMs - bucket.lastRefillMs);
            long addedScaled = (elapsedMs * properties.refillTokens() * TOKEN_SCALE) / refillPeriodMs;
            if (addedScaled > 0L) {
                bucket.scaledTokens = Math.min(capacityScaled, bucket.scaledTokens + addedScaled);
                bucket.lastRefillMs = nowMs;
            }

            boolean allowed = false;
            if (bucket.scaledTokens >= TOKEN_SCALE) {
                bucket.scaledTokens -= TOKEN_SCALE;
                allowed = true;
            }

            int remaining = (int) Math.max(0L, bucket.scaledTokens / TOKEN_SCALE);
            long missingScaled = Math.max(0L, capacityScaled - bucket.scaledTokens);
            long denomPerSec = Math.multiplyExact((long) properties.refillTokens(), TOKEN_SCALE);
            long timeToFullMs = missingScaled == 0L ? 0L : ceilDiv(missingScaled * refillPeriodMs, denomPerSec);
            long resetEpochSeconds = (nowMs + timeToFullMs + 999L) / 1000L;

            long retryAfterSeconds = 0L;
            if (!allowed) {
                long neededScaled = Math.max(0L, TOKEN_SCALE - bucket.scaledTokens);
                long numerator = neededScaled * refillPeriodMs;
                long denominator = denomPerSec * 1000L;
                retryAfterSeconds = Math.max(1L, ceilDiv(numerator, denominator));
            }

            return new RateLimitDecision(allowed, properties.capacity(), remaining, resetEpochSeconds, retryAfterSeconds);
        }
    }

    private static long ceilDiv(long numerator, long denominator) {
        if (numerator <= 0L) {
            return 0L;
        }
        return (numerator + denominator - 1L) / denominator;
    }
}
