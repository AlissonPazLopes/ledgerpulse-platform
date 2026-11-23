package dev.ledgerpulse.webhook.sink;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class WebhookSinkStateService {

    public record SinkState(SinkMode mode, Instant updatedAt) {
    }

    private final AtomicReference<SinkState> stateRef;
    private final long timeoutDelayMs;

    public WebhookSinkStateService(@Value("${app.sink.timeout-delay-ms:2500}") long timeoutDelayMs) {
        this.stateRef = new AtomicReference<>(new SinkState(SinkMode.OK_200, Instant.now()));
        this.timeoutDelayMs = Math.max(10L, timeoutDelayMs);
    }

    public SinkState getCurrentState() {
        return stateRef.get();
    }

    public SinkState updateMode(SinkMode mode) {
        Objects.requireNonNull(mode, "SinkMode must not be null");
        SinkState updated = new SinkState(mode, Instant.now());
        stateRef.set(updated);
        return updated;
    }

    public long getTimeoutDelayMs() {
        return timeoutDelayMs;
    }
}
