CREATE TABLE ledger_core.users (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'ANALYST', 'VIEWER')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE ledger_core.merchants (
    id              UUID         PRIMARY KEY,
    name            VARCHAR(160) NOT NULL,
    api_key_hash    VARCHAR(64)  NOT NULL,
    default_fee_bps INTEGER      NOT NULL DEFAULT 250,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_merchants_api_key_hash UNIQUE (api_key_hash),
    CONSTRAINT chk_merchants_default_fee_bps CHECK (default_fee_bps >= 0 AND default_fee_bps <= 10000),
    CONSTRAINT chk_merchants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE ledger_core.accounts (
    id                      UUID         PRIMARY KEY,
    merchant_id             UUID         NULL,
    account_type            VARCHAR(30)  NOT NULL,
    holder_name             VARCHAR(160) NOT NULL,
    currency                VARCHAR(3)   NOT NULL DEFAULT 'BRL',
    available_balance_cents BIGINT       NOT NULL DEFAULT 0,
    pending_balance_cents   BIGINT       NOT NULL DEFAULT 0,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version                 BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_accounts_merchant FOREIGN KEY (merchant_id) REFERENCES ledger_core.merchants (id),
    CONSTRAINT chk_accounts_type CHECK (account_type IN ('MERCHANT', 'PLATFORM_FEE', 'ESCROW', 'RECIPIENT')),
    CONSTRAINT chk_accounts_currency CHECK (currency = 'BRL'),
    CONSTRAINT chk_accounts_pending_balance CHECK (pending_balance_cents >= 0),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
);

CREATE INDEX idx_accounts_merchant_id ON ledger_core.accounts (merchant_id);
CREATE INDEX idx_accounts_type ON ledger_core.accounts (account_type);
