package dev.ledgerpulse.core.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "app.idempotency.store", havingValue = "memory")
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private record Entry(StoredIdempotencyRecord record, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return expiresAt.isBefore(now);
        }
    }

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Optional<StoredIdempotencyRecord> find(String compositeKey) {
        Instant now = Instant.now();
        Entry entry = store.get(compositeKey);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired(now)) {
            store.remove(compositeKey, entry);
            return Optional.empty();
        }
        return Optional.of(entry.record());
    }

    @Override
    public boolean tryAcquireInProgress(String compositeKey, String requestHash, Duration ttl) {
        Instant now = Instant.now();
        AtomicBoolean acquired = new AtomicBoolean(false);
        store.compute(compositeKey, (key, current) -> {
            if (current == null || current.isExpired(now)) {
                acquired.set(true);
                return new Entry(
                        new StoredIdempotencyRecord(StoredIdempotencyRecord.State.IN_PROGRESS, requestHash, null, null),
                        now.plus(ttl)
                );
            }
            return current;
        });
        return acquired.get();
    }

    @Override
    public void saveCompleted(String compositeKey, String requestHash, int httpStatus, String responseJson, Duration ttl) {
        store.put(
                compositeKey,
                new Entry(
                        new StoredIdempotencyRecord(StoredIdempotencyRecord.State.COMPLETED, requestHash, httpStatus, responseJson),
                        Instant.now().plus(ttl)
                )
        );
    }

    @Override
    public void release(String compositeKey) {
        store.remove(compositeKey);
    }
}
