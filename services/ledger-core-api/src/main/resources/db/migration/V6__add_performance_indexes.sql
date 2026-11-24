CREATE INDEX idx_payments_merchant_status_created
    ON ledger_core.payments (merchant_id, status, created_at DESC);

CREATE INDEX idx_payments_status_risk_created
    ON ledger_core.payments (status, risk_score DESC, created_at DESC);

CREATE INDEX idx_payments_payer_created
    ON ledger_core.payments (payer_id, created_at DESC);

CREATE INDEX idx_payment_refunds_payment_created
    ON ledger_core.payment_refunds (payment_id, created_at DESC);

CREATE INDEX idx_reconciliation_reports_created
    ON ledger_core.reconciliation_reports (created_at DESC);
