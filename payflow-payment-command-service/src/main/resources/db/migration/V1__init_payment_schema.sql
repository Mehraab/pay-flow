CREATE TABLE payments (
    id               UUID PRIMARY KEY,
    merchant_id      UUID NOT NULL,
    amount_minor     BIGINT NOT NULL,
    currency         VARCHAR(3) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    idempotency_key  VARCHAR(255) NOT NULL,
    provider_ref     VARCHAR(100),
    decline_reason   VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, idempotency_key)
);
CREATE INDEX idx_payments_merchant_status ON payments (merchant_id, status);

CREATE TABLE outbox_events (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type   VARCHAR(50) NOT NULL,
    aggregate_id     UUID NOT NULL,
    event_type       VARCHAR(50) NOT NULL,
    payload          JSONB NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at     TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
