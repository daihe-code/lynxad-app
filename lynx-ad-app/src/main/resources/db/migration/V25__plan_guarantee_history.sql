CREATE TABLE delivery_guarantee_history (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    reason TEXT,
    detail_url TEXT,
    observed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_guarantee_plan_time ON delivery_guarantee_history(tenant_id,plan_id,observed_at);
