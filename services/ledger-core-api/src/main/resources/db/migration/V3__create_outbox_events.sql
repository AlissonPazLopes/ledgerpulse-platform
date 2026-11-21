CREATE TABLE ledger_core.outbox_events (
    id             UUID                     PRIMARY KEY,
    aggregate_type VARCHAR(40)              NOT NULL,
    aggregate_id   UUID                     NOT NULL,
    merchant_id    UUID                     NOT NULL,
    event_type     VARCHAR(80)              NOT NULL,
    correlation_id VARCHAR(64)              NOT NULL,
    payload_json   TEXT                     NOT NULL,
    status         VARCHAR(20)              NOT NULL DEFAULT 'PENDING',
    attempts       INTEGER                  NOT NULL DEFAULT 0,
    last_error     TEXT                     NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at   TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_outbox_events_merchant FOREIGN KEY (merchant_id) REFERENCES ledger_core.merchants (id),
    CONSTRAINT chk_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT chk_outbox_events_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_status_created ON ledger_core.outbox_events (status, created_at ASC);
CREATE INDEX idx_outbox_aggregate ON ledger_core.outbox_events (aggregate_type, aggregate_id);
