CREATE TABLE webhooks.webhook_endpoints (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    target_url VARCHAR(500) NOT NULL,
    signing_secret VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE webhooks.webhook_deliveries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    endpoint_id UUID NOT NULL REFERENCES webhooks.webhook_endpoints(id),
    merchant_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RETRYING', 'DELIVERED', 'DLQ')),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 4,
    last_http_status INTEGER NULL,
    last_latency_ms BIGINT NULL,
    last_error TEXT NULL,
    signature_header VARCHAR(255) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT uq_webhook_deliveries_event_endpoint UNIQUE (event_id, endpoint_id)
);

CREATE TABLE webhooks.webhook_delivery_attempts (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES webhooks.webhook_deliveries(id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    http_status INTEGER NULL,
    latency_ms BIGINT NOT NULL CHECK (latency_ms >= 0),
    signature_header VARCHAR(255) NOT NULL,
    error_message TEXT NULL,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_endpoints_merchant_status
    ON webhooks.webhook_endpoints (merchant_id, status);

CREATE INDEX idx_webhook_deliveries_status_created
    ON webhooks.webhook_deliveries (status, created_at DESC);

CREATE INDEX idx_webhook_deliveries_merchant_created
    ON webhooks.webhook_deliveries (merchant_id, created_at DESC);

CREATE INDEX idx_webhook_delivery_attempts_delivery_num
    ON webhooks.webhook_delivery_attempts (delivery_id, attempt_number ASC);
