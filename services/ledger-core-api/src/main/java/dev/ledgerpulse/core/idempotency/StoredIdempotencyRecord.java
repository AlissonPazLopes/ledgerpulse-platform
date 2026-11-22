package dev.ledgerpulse.core.idempotency;

public record StoredIdempotencyRecord(
        State state,
        String requestHash,
        Integer httpStatus,
        String responseJson
) {
    public enum State {
        IN_PROGRESS,
        COMPLETED
    }
}
