package dev.ledgerpulse.core.reconciliation.service;

import dev.ledgerpulse.core.reconciliation.domain.ReconciliationTrigger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.reconciliation.scheduled.enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationScheduler {

    private final ReconciliationService reconciliationService;

    public ReconciliationScheduler(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(
            fixedDelayString = "${app.reconciliation.scheduled.fixed-delay-ms:300000}",
            initialDelayString = "${app.reconciliation.scheduled.initial-delay-ms:60000}"
    )
    public void runScheduledReconciliation() {
        reconciliationService.runReconciliation(ReconciliationTrigger.SCHEDULED, UUID.randomUUID().toString());
    }
}
