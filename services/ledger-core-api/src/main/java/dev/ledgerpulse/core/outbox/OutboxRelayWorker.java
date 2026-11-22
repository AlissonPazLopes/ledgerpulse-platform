package dev.ledgerpulse.core.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayWorker.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final int maxAttempts;

    public OutboxRelayWorker(
            OutboxEventRepository outboxEventRepository,
            OutboxEventPublisher outboxEventPublisher,
            TransactionTemplate transactionTemplate,
            @Value("${app.outbox.relay.max-attempts:5}") int maxAttempts
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEventPublisher = outboxEventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay.fixed-delay-ms:2000}")
    public void relayPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        for (OutboxEvent event : pending) {
            processSingleEvent(event.getId());
        }
    }

    private void processSingleEvent(java.util.UUID eventId) {
        transactionTemplate.executeWithoutResult(status -> {
            OutboxEvent managed = outboxEventRepository.findById(eventId).orElse(null);
            if (managed == null || managed.getStatus() != OutboxStatus.PENDING) {
                return;
            }
            try {
                outboxEventPublisher.publish(managed);
                managed.markPublished(Instant.now());
            } catch (Exception ex) {
                log.warn("Falha ao publicar evento outbox id={} (tentativa {}/{}): {}",
                        managed.getId(), managed.getAttempts() + 1, maxAttempts, ex.getMessage());
                managed.recordFailure(ex.getMessage(), maxAttempts);
            }
            outboxEventRepository.save(managed);
        });
    }
}
