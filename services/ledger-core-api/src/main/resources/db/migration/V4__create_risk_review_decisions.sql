CREATE TABLE ledger_core.risk_review_decisions (
    id               UUID                     PRIMARY KEY,
    payment_id       UUID                     NOT NULL,
    reviewer_user_id UUID                     NOT NULL,
    reviewer_email   VARCHAR(160)             NOT NULL,
    decision         VARCHAR(20)              NOT NULL,
    justification    VARCHAR(500)             NOT NULL,
    previous_status  VARCHAR(30)              NOT NULL,
    resulting_status VARCHAR(30)              NOT NULL,
    correlation_id   VARCHAR(64)              NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_risk_review_decisions_payment UNIQUE (payment_id),
    CONSTRAINT fk_risk_review_decisions_payment FOREIGN KEY (payment_id) REFERENCES ledger_core.payments (id),
    CONSTRAINT fk_risk_review_decisions_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES ledger_core.users (id),
    CONSTRAINT chk_risk_review_decision CHECK (decision IN ('APPROVE', 'DECLINE')),
    CONSTRAINT chk_risk_review_resulting_status CHECK (resulting_status IN ('SETTLED', 'DECLINED'))
);

CREATE INDEX idx_risk_review_decisions_created ON ledger_core.risk_review_decisions (created_at DESC);
