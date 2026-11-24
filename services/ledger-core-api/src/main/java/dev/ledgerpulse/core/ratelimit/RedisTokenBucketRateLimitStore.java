package dev.ledgerpulse.core.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "redis", matchIfMissing = true)
public class RedisTokenBucketRateLimitStore implements RateLimitStore {

    private static final String KEY_PREFIX = "ledgerpulse:ratelimit:";

    static final String TOKEN_BUCKET_LUA = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refillTokens = tonumber(ARGV[2])
            local refillPeriodMs = tonumber(ARGV[3])
            local nowMs = tonumber(ARGV[4])

            local data = redis.call('HMGET', key, 'tokens', 'lastRefillMs')
            local tokens = tonumber(data[1])
            local lastRefillMs = tonumber(data[2])

            if tokens == nil or lastRefillMs == nil then
                tokens = capacity
                lastRefillMs = nowMs
            else
                local elapsedMs = math.max(0, nowMs - lastRefillMs)
                local added = (elapsedMs * refillTokens) / refillPeriodMs
                if added > 0 then
                    tokens = math.min(capacity, tokens + added)
                    lastRefillMs = nowMs
                end
            end

            local allowed = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            end

            local missingTokens = math.max(0, capacity - tokens)
            local timeToFullMs = 0
            if refillTokens > 0 and missingTokens > 0 then
                timeToFullMs = math.ceil((missingTokens * refillPeriodMs) / refillTokens)
            end
            local resetEpochSeconds = math.floor((nowMs + timeToFullMs + 999) / 1000)

            local retryAfterSeconds = 0
            if allowed == 0 and refillTokens > 0 then
                local needed = math.max(0, 1 - tokens)
                retryAfterSeconds = math.max(1, math.ceil((needed * refillPeriodMs) / (refillTokens * 1000)))
            end

            local ttlSeconds = math.max(60, math.ceil(refillPeriodMs / 1000) * 2)
            redis.call('HSET', key, 'tokens', string.format('%.6f', tokens), 'lastRefillMs', tostring(lastRefillMs))
            redis.call('EXPIRE', key, ttlSeconds)

            return { allowed, math.floor(tokens), resetEpochSeconds, retryAfterSeconds }
            """;

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final DefaultRedisScript<List> tokenBucketScript;

    public RedisTokenBucketRateLimitStore(
            StringRedisTemplate redisTemplate,
            RateLimitProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setScriptText(TOKEN_BUCKET_LUA);
        this.tokenBucketScript.setResultType(List.class);
    }

    @Override
    public RateLimitDecision tryConsume(String key) {
        long nowMs = Instant.now().toEpochMilli();
        long refillPeriodMs = Math.multiplyExact(properties.refillPeriodSeconds(), 1000L);
        List<?> result = redisTemplate.execute(
                tokenBucketScript,
                List.of(KEY_PREFIX + key),
                String.valueOf(properties.capacity()),
                String.valueOf(properties.refillTokens()),
                String.valueOf(refillPeriodMs),
                String.valueOf(nowMs)
        );
        if (result == null || result.size() < 4) {
            throw new IllegalStateException("Resposta inválida do script Redis Token Bucket.");
        }

        boolean allowed = asLong(result.get(0)) == 1L;
        int remaining = (int) Math.max(0L, asLong(result.get(1)));
        long resetEpochSeconds = asLong(result.get(2));
        long retryAfterSeconds = Math.max(0L, asLong(result.get(3)));
        return new RateLimitDecision(allowed, properties.capacity(), remaining, resetEpochSeconds, retryAfterSeconds);
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
