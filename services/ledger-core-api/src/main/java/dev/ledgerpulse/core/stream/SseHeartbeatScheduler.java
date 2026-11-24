package dev.ledgerpulse.core.stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.sse.heartbeat-enabled", havingValue = "true", matchIfMissing = true)
public class SseHeartbeatScheduler {

    private final SseEventHub sseEventHub;

    public SseHeartbeatScheduler(SseEventHub sseEventHub) {
        this.sseEventHub = sseEventHub;
    }

    @Scheduled(fixedRateString = "${app.sse.heartbeat-interval-ms:15000}")
    public void sendHeartbeat() {
        sseEventHub.sendHeartbeat();
    }
}
