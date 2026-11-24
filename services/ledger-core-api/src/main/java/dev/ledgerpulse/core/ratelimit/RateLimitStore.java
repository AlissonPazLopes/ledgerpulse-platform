package dev.ledgerpulse.core.ratelimit;

public interface RateLimitStore {

    RateLimitDecision tryConsume(String key);
}
