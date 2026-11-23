package dev.ledgerpulse.webhook.messaging;

public final class RabbitTopologyConstants {

    public static final String LEDGER_EVENTS_EXCHANGE = "ledger.events";
    public static final String LEDGER_RETRY_EXCHANGE = "ledger.retry";
    public static final String LEDGER_DLX_EXCHANGE = "ledger.dlx";

    public static final String DISPATCH_QUEUE = "ledger.webhooks.dispatch";
    public static final String RETRY_5S_QUEUE = "ledger.retry.5s";
    public static final String RETRY_30S_QUEUE = "ledger.retry.30s";
    public static final String RETRY_120S_QUEUE = "ledger.retry.120s";
    public static final String DLQ_QUEUE = "ledger.webhooks.dlq";

    public static final String EVENTS_BINDING_KEY = "#";
    public static final String RETRY_5S_ROUTING_KEY = "retry.5s";
    public static final String RETRY_30S_ROUTING_KEY = "retry.30s";
    public static final String RETRY_120S_ROUTING_KEY = "retry.120s";
    public static final String DLQ_ROUTING_KEY = "webhooks.dlq";

    public static final long RETRY_5S_TTL_MS = 5_000L;
    public static final long RETRY_30S_TTL_MS = 30_000L;
    public static final long RETRY_120S_TTL_MS = 120_000L;

    private RabbitTopologyConstants() {
    }
}
