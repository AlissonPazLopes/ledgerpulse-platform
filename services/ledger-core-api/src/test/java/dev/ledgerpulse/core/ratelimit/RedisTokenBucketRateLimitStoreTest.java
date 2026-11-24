package dev.ledgerpulse.core.ratelimit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisTokenBucketRateLimitStoreTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteLuaScriptAndMapAllowedAndDeniedDecisions() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RateLimitProperties properties = new RateLimitProperties(true, "redis", 60, 60, 60);
        RedisTokenBucketRateLimitStore store = new RedisTokenBucketRateLimitStore(redisTemplate, properties);

        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(1L, 59L, 1795500060L, 0L))
                .thenReturn(List.of(0L, 0L, 1795500060L, 12L));

        RateLimitDecision allowedDecision = store.tryConsume("merchant:abc");
        assertThat(allowedDecision.allowed()).isTrue();
        assertThat(allowedDecision.limit()).isEqualTo(60);
        assertThat(allowedDecision.remaining()).isEqualTo(59);
        assertThat(allowedDecision.resetEpochSeconds()).isEqualTo(1795500060L);
        assertThat(allowedDecision.retryAfterSeconds()).isZero();

        ArgumentCaptor<RedisScript<List>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                eq(List.of("ledgerpulse:ratelimit:merchant:abc")),
                eq("60"),
                eq("60"),
                eq("60000"),
                any()
        );
        assertThat(scriptCaptor.getValue().getScriptAsString()).contains("HMGET");
        assertThat(scriptCaptor.getValue().getScriptAsString()).contains("EXPIRE");

        RateLimitDecision deniedDecision = store.tryConsume("merchant:abc");
        assertThat(deniedDecision.allowed()).isFalse();
        assertThat(deniedDecision.limit()).isEqualTo(60);
        assertThat(deniedDecision.remaining()).isZero();
        assertThat(deniedDecision.retryAfterSeconds()).isEqualTo(12L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowWhenRedisReturnsNullOrMalformedTuple() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RateLimitProperties properties = new RateLimitProperties(true, "redis", 60, 60, 60);
        RedisTokenBucketRateLimitStore store = new RedisTokenBucketRateLimitStore(redisTemplate, properties);

        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(null);

        assertThatThrownBy(() -> store.tryConsume("merchant:broken"))
                .isInstanceOf(IllegalStateException.class);
    }
}
