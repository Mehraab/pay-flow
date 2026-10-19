CREATE TABLE webhook_endpoints (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id      UUID NOT NULL,
    url              VARCHAR(500) NOT NULL,
    signing_secret   VARCHAR(100) NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE webhook_deliveries (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id      UUID,
    event_type       VARCHAR(50) NOT NULL,
    payload          JSONB NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING','DELIVERED','FAILED','DEAD_LETTER')),
    attempt_count    INT NOT NULL DEFAULT 0,
    response_code    INT,
    last_attempt_at  TIMESTAMPTZ,
    next_retry_at    TIMESTAMPTZ
);
CREATE INDEX idx_webhook_due ON webhook_deliveries (next_retry_at) WHERE status = 'PENDING';
