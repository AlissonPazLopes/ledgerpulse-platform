CREATE TABLE ledger_core.payment_refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES ledger_core.payments(id),
    merchant_id UUID NOT NULL REFERENCES ledger_core.merchants(id),
    ledger_transaction_id UUID NOT NULL REFERENCES ledger_core.ledger_transactions(id),
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    platform_fee_reversed_cents BIGINT NOT NULL CHECK (platform_fee_reversed_cents >= 0),
    net_merchant_reversed_cents BIGINT NOT NULL CHECK (net_merchant_reversed_cents >= 0),
    recipient_reversals_json TEXT NOT NULL DEFAULT '[]',
    reason VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_refunds_merchant_idempotency UNIQUE (merchant_id, idempotency_key)
);

CREATE TABLE ledger_core.reconciliation_reports (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL CHECK (status IN ('BALANCED', 'DIVERGENT')),
    checked_accounts_count INTEGER NOT NULL CHECK (checked_accounts_count >= 0),
    divergent_accounts_count INTEGER NOT NULL CHECK (divergent_accounts_count >= 0),
    checked_transactions_count INTEGER NOT NULL CHECK (checked_transactions_count >= 0),
    unbalanced_transactions_count INTEGER NOT NULL CHECK (unbalanced_transactions_count >= 0),
    total_settled_payments_cents BIGINT NOT NULL CHECK (total_settled_payments_cents >= 0),
    total_ledger_settlements_cents BIGINT NOT NULL CHECK (total_ledger_settlements_cents >= 0),
    total_refunds_cents BIGINT NOT NULL CHECK (total_refunds_cents >= 0),
    total_ledger_refunds_cents BIGINT NOT NULL CHECK (total_ledger_refunds_cents >= 0),
    discrepancy_cents BIGINT NOT NULL,
    trigger_source VARCHAR(20) NOT NULL CHECK (trigger_source IN ('MANUAL', 'SCHEDULED')),
    findings_json TEXT NOT NULL DEFAULT '[]',
    correlation_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
