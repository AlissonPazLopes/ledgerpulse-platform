package dev.ledgerpulse.core.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisIdempotencyStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisIdempotencyStore store;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        store = new RedisIdempotencyStore(redisTemplate, objectMapper);
    }

    @Test
    void shouldAcquireSaveReadAndReleaseRedisIdempotencyKeyWithTtl() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("ledgerpulse:idempotency:m1:key-1"),
                anyString(),
                eq(Duration.ofHours(24))
        )).thenReturn(true);

        boolean acquired = store.tryAcquireInProgress("m1:key-1", "hash-abc", Duration.ofHours(24));
        assertThat(acquired).isTrue();

        store.saveCompleted("m1:key-1", "hash-abc", 201, "{\"id\":\"123\"}", Duration.ofHours(24));
        verify(valueOperations).set(
                eq("ledgerpulse:idempotency:m1:key-1"),
                anyString(),
                eq(Duration.ofHours(24))
        );

        String storedJson = objectMapper.writeValueAsString(new StoredIdempotencyRecord(
                StoredIdempotencyRecord.State.COMPLETED,
                "hash-abc",
                201,
                "{\"id\":\"123\"}"
        ));
        when(valueOperations.get("ledgerpulse:idempotency:m1:key-1")).thenReturn(storedJson);

        Optional<StoredIdempotencyRecord> found = store.find("m1:key-1");
        assertThat(found).isPresent();
        assertThat(found.get().state()).isEqualTo(StoredIdempotencyRecord.State.COMPLETED);
        assertThat(found.get().httpStatus()).isEqualTo(201);
        assertThat(found.get().responseJson()).isEqualTo("{\"id\":\"123\"}");

        store.release("m1:key-1");
        verify(redisTemplate).delete("ledgerpulse:idempotency:m1:key-1");
    }
}
