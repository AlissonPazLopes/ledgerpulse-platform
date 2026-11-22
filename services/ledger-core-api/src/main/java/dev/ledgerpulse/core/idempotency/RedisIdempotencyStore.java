package dev.ledgerpulse.core.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.idempotency.store", havingValue = "redis", matchIfMissing = true)
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String KEY_PREFIX = "ledgerpulse:idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<StoredIdempotencyRecord> find(String compositeKey) {
        String raw = redisTemplate.opsForValue().get(KEY_PREFIX + compositeKey);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, StoredIdempotencyRecord.class));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao desserializar registro idempotente no Redis", ex);
        }
    }

    @Override
    public boolean tryAcquireInProgress(String compositeKey, String requestHash, Duration ttl) {
        StoredIdempotencyRecord record = new StoredIdempotencyRecord(
                StoredIdempotencyRecord.State.IN_PROGRESS,
                requestHash,
                null,
                null
        );
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + compositeKey, serialize(record), ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void saveCompleted(String compositeKey, String requestHash, int httpStatus, String responseJson, Duration ttl) {
        StoredIdempotencyRecord record = new StoredIdempotencyRecord(
                StoredIdempotencyRecord.State.COMPLETED,
                requestHash,
                httpStatus,
                responseJson
        );
        redisTemplate.opsForValue().set(KEY_PREFIX + compositeKey, serialize(record), ttl);
    }

    @Override
    public void release(String compositeKey) {
        redisTemplate.delete(KEY_PREFIX + compositeKey);
    }

    private String serialize(StoredIdempotencyRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar registro idempotente no Redis", ex);
        }
    }
}
