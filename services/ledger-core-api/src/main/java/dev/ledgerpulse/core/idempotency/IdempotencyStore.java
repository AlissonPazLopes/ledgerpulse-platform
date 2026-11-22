package dev.ledgerpulse.core.idempotency;

import java.time.Duration;
import java.util.Optional;

public interface IdempotencyStore {

    Optional<StoredIdempotencyRecord> find(String compositeKey);

    boolean tryAcquireInProgress(String compositeKey, String requestHash, Duration ttl);

    void saveCompleted(String compositeKey, String requestHash, int httpStatus, String responseJson, Duration ttl);

    void release(String compositeKey);
}
