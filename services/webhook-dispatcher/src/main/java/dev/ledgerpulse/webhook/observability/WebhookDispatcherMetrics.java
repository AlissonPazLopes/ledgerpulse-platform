package dev.ledgerpulse.webhook.observability;

import dev.ledgerpulse.webhook.domain.WebhookDeliveryStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class WebhookDispatcherMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer webhookDeliveryTimer;

    public WebhookDispatcherMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.webhookDeliveryTimer = Timer.builder("webhook_delivery_latency")
                .description("Latency of outbound HMAC webhook HTTP delivery calls")
                .publishPercentileHistogram(true)
                .register(meterRegistry);
    }

    public Timer.Sample startDeliveryTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopDeliveryTimer(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(webhookDeliveryTimer);
        }
    }

    public void recordDeliveryOutcome(WebhookDeliveryStatus status, String eventType) {
        String statusTag = status != null ? status.name() : "UNKNOWN";
        String eventTypeTag = (eventType != null && !eventType.isBlank()) ? eventType : "unknown";

        Counter.builder("webhook_deliveries_total")
                .description("Total webhook delivery attempts by resulting status and eventType")
                .tag("status", statusTag)
                .tag("eventType", eventTypeTag)
                .register(meterRegistry)
                .increment();

        if (status == WebhookDeliveryStatus.DLQ) {
            Counter.builder("webhook_dlq_total")
                    .description("Total webhook deliveries routed to DLQ after retry exhaustion or irreversible error")
                    .tag("eventType", eventTypeTag)
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void recordDlqReplay(WebhookDeliveryStatus status) {
        String statusTag = status != null ? status.name() : "UNKNOWN";
        Counter.builder("webhook_dlq_replays_total")
                .description("Total manual DLQ webhook replays by outcome status")
                .tag("status", statusTag)
                .register(meterRegistry)
                .increment();
    }
}
