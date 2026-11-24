package dev.ledgerpulse.core.stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SseBroadcastListener {

    private final SseEventHub sseEventHub;

    public SseBroadcastListener(SseEventHub sseEventHub) {
        this.sseEventHub = sseEventHub;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDomainStreamEvent(DomainStreamEvent event) {
        if (event != null && event.envelope() != null) {
            sseEventHub.broadcastDomainEvent(event.envelope());
        }
    }
}
