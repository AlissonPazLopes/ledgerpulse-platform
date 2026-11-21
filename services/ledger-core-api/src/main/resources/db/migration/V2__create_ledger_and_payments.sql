CREATE TABLE ledger_core.ledger_transactions (
    id             UUID         PRIMARY KEY,
    reference_type VARCHAR(40)  NOT NULL,
    reference_id   UUID         NOT NULL,
    description    VARCHAR(255) NOT NULL,
    currency       VARCHAR(3)   NOT NULL DEFAULT 'BRL',
    correlation_id VARCHAR(64)  NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ledger_tx_reference_type CHECK (reference_type IN ('PAYMENT_SETTLEMENT', 'PAYMENT_REFUND', 'MANUAL_ADJUSTMENT')),
    CONSTRAINT chk_ledger_tx_currency CHECK (currency = 'BRL')
);

CREATE TABLE ledger_core.ledger_entries (
    id                    UUID        PRIMARY KEY,
    ledger_transaction_id UUID        NOT NULL,
    account_id            UUID        NOT NULL,
    direction             VARCHAR(10) NOT NULL,
    amount_cents          BIGINT      NOT NULL,
    balance_after_cents   BIGINT      NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ledger_entries_tx FOREIGN KEY (ledger_transaction_id) REFERENCES ledger_core.ledger_transactions (id),
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (account_id) REFERENCES ledger_core.accounts (id),
    CONSTRAINT chk_ledger_entries_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_entries_amount_positive CHECK (amount_cents > 0)
);

CREATE TABLE ledger_core.payments (
    id                    UUID         PRIMARY KEY,
    merchant_id           UUID         NOT NULL,
    payer_id              VARCHAR(120) NOT NULL,
    amount_cents          BIGINT       NOT NULL,
    platform_fee_cents    BIGINT       NOT NULL DEFAULT 0,
    net_merchant_cents    BIGINT       NOT NULL DEFAULT 0,
    currency              VARCHAR(3)   NOT NULL DEFAULT 'BRL',
    payment_method        VARCHAR(30)  NOT NULL,
    description           VARCHAR(255) NULL,
    status                VARCHAR(30)  NOT NULL,
    risk_score            INTEGER      NULL,
    risk_decision         VARCHAR(20)  NULL,
    reason_codes_json     TEXT         NOT NULL DEFAULT '[]',
    idempotency_key       VARCHAR(128) NOT NULL,
    correlation_id        VARCHAR(64)  NOT NULL,
    ledger_transaction_id UUID         NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_merchant FOREIGN KEY (merchant_id) REFERENCES ledger_core.merchants (id),
    CONSTRAINT fk_payments_ledger_tx FOREIGN KEY (ledger_transaction_id) REFERENCES ledger_core.ledger_transactions (id),
    CONSTRAINT uq_payments_merchant_idempotency UNIQUE (merchant_id, idempotency_key),
    CONSTRAINT chk_payments_amount_positive CHECK (amount_cents > 0),
    CONSTRAINT chk_payments_platform_fee CHECK (platform_fee_cents >= 0),
    CONSTRAINT chk_payments_net_merchant CHECK (net_merchant_cents >= 0),
    CONSTRAINT chk_payments_currency CHECK (currency = 'BRL'),
    CONSTRAINT chk_payments_method CHECK (payment_method IN ('CARD', 'PIX', 'BOLETO')),
    CONSTRAINT chk_payments_status CHECK (status IN ('CREATED', 'RISK_ANALYSIS', 'AUTHORIZED', 'MANUAL_REVIEW', 'SETTLED', 'DECLINED', 'PARTIALLY_REFUNDED', 'REFUNDED')),
    CONSTRAINT chk_payments_risk_score CHECK (risk_score IS NULL OR (risk_score >= 0 AND risk_score <= 100)),
    CONSTRAINT chk_payments_risk_decision CHECK (risk_decision IS NULL OR risk_decision IN ('APPROVE', 'REVIEW', 'BLOCK'))
);

CREATE TABLE ledger_core.payment_splits (
    id                   UUID        PRIMARY KEY,
    payment_id           UUID        NOT NULL,
    recipient_account_id UUID        NOT NULL,
    split_type           VARCHAR(20) NOT NULL,
    percentage_bps       INTEGER     NULL,
    amount_cents         BIGINT      NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_splits_payment FOREIGN KEY (payment_id) REFERENCES ledger_core.payments (id),
    CONSTRAINT fk_payment_splits_recipient FOREIGN KEY (recipient_account_id) REFERENCES ledger_core.accounts (id),
    CONSTRAINT uq_payment_splits_payment_recipient UNIQUE (payment_id, recipient_account_id),
    CONSTRAINT chk_payment_splits_type CHECK (split_type IN ('PERCENTAGE', 'FIXED')),
    CONSTRAINT chk_payment_splits_percentage_bps CHECK (percentage_bps IS NULL OR (percentage_bps > 0 AND percentage_bps <= 10000)),
    CONSTRAINT chk_payment_splits_amount_positive CHECK (amount_cents > 0)
);

CREATE INDEX idx_payments_merchant_created ON ledger_core.payments (merchant_id, created_at DESC);
CREATE INDEX idx_payments_status_created ON ledger_core.payments (status, created_at DESC);
CREATE INDEX idx_payments_correlation ON ledger_core.payments (correlation_id);
CREATE INDEX idx_payment_splits_payment ON ledger_core.payment_splits (payment_id);
CREATE INDEX idx_ledger_tx_reference ON ledger_core.ledger_transactions (reference_type, reference_id);
CREATE INDEX idx_ledger_tx_created ON ledger_core.ledger_transactions (created_at DESC);
CREATE INDEX idx_ledger_entries_tx ON ledger_core.ledger_entries (ledger_transaction_id);
CREATE INDEX idx_ledger_entries_account_created ON ledger_core.ledger_entries (account_id, created_at DESC);
