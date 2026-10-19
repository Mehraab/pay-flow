CREATE TABLE ledger_accounts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id      UUID,                       -- NULL for platform-level accounts
    account_type     VARCHAR(20) NOT NULL
                       CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    account_name     VARCHAR(100) NOT NULL,       -- 'Platform Bank', 'Merchant Payable', 'Platform Fee Revenue'
    normal_balance   VARCHAR(10) NOT NULL CHECK (normal_balance IN ('DEBIT','CREDIT')),
    balance_minor    BIGINT NOT NULL DEFAULT 0,
    UNIQUE (merchant_id, account_name)
);

CREATE TABLE ledger_entries (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_ref   VARCHAR(100) NOT NULL,      -- payment id
    debit_account_id  UUID NOT NULL REFERENCES ledger_accounts(id),
    credit_account_id UUID NOT NULL REFERENCES ledger_accounts(id),
    amount_minor      BIGINT NOT NULL CHECK (amount_minor > 0),
    posted_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (debit_account_id <> credit_account_id)
);
CREATE INDEX idx_ledger_entries_ref ON ledger_entries (transaction_ref);

CREATE TABLE processed_events (
    event_id       UUID NOT NULL,
    consumer_name  VARCHAR(50) NOT NULL,
    processed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer_name)
);
