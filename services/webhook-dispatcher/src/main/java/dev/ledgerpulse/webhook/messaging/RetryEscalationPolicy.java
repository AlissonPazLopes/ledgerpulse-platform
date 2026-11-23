package dev.ledgerpulse.webhook.messaging;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Determines the next RabbitMQ escalation step (5s -> 30s -> 120s -> DLQ)
 * from RabbitMQ's x-death headers across retry queues.
 */
@Component
public class RetryEscalationPolicy {

    private static final String RETRY_QUEUE_PREFIX = "ledger.retry.";

    public enum Target {
        RETRY,
        DLQ
    }

    public record RouteDecision(
            Target target,
            String exchange,
            String routingKey,
            long priorRetryCount
    ) {
        public boolean isDlq() {
            return target == Target.DLQ;
        }
    }

    public long countPriorRetries(List<Map<String, ?>> xDeathHeader) {
        if (xDeathHeader == null || xDeathHeader.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (Map<String, ?> entry : xDeathHeader) {
            if (entry == null) {
                continue;
            }
            Object queueObj = entry.get("queue");
            if (queueObj instanceof String queueName && queueName.startsWith(RETRY_QUEUE_PREFIX)) {
                Object countObj = entry.get("count");
                if (countObj instanceof Number countNumber) {
                    total += countNumber.longValue();
                }
            }
        }
        return total;
    }

    public RouteDecision decide(List<Map<String, ?>> xDeathHeader) {
        long priorRetries = countPriorRetries(xDeathHeader);
        return decideForRetryCount(priorRetries);
    }

    public RouteDecision decideForRetryCount(long priorRetries) {
        if (priorRetries <= 0L) {
            return new RouteDecision(
                    Target.RETRY,
                    RabbitTopologyConstants.LEDGER_RETRY_EXCHANGE,
                    RabbitTopologyConstants.RETRY_5S_ROUTING_KEY,
                    0L
            );
        }
        if (priorRetries == 1L) {
            return new RouteDecision(
                    Target.RETRY,
                    RabbitTopologyConstants.LEDGER_RETRY_EXCHANGE,
                    RabbitTopologyConstants.RETRY_30S_ROUTING_KEY,
                    1L
            );
        }
        if (priorRetries == 2L) {
            return new RouteDecision(
                    Target.RETRY,
                    RabbitTopologyConstants.LEDGER_RETRY_EXCHANGE,
                    RabbitTopologyConstants.RETRY_120S_ROUTING_KEY,
                    2L
            );
        }
        return dlqDecision(priorRetries);
    }

    public RouteDecision dlqDecision(long priorRetries) {
        return new RouteDecision(
                Target.DLQ,
                RabbitTopologyConstants.LEDGER_DLX_EXCHANGE,
                RabbitTopologyConstants.DLQ_ROUTING_KEY,
                priorRetries
        );
    }
}
