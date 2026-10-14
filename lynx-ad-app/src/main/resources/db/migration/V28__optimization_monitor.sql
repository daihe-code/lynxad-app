CREATE TABLE optimization_policy (
    tenant_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    policy_version BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    config_json TEXT NOT NULL,
    next_check_at TIMESTAMPTZ NOT NULL,
    lease_owner VARCHAR(64),
    lease_until TIMESTAMPTZ,
    last_check_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    result_json TEXT,
    PRIMARY KEY (tenant_id,plan_id)
);
CREATE INDEX idx_optimization_policy_due ON optimization_policy(enabled,next_check_at,lease_until);
CREATE TABLE product_economics_profile (
    tenant_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    aweme_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    contribution_margin_rate DECIMAL(12,8) NOT NULL,
    refund_reserve_rate DECIMAL(12,8) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id,account_id,aweme_id,product_id)
);
CREATE TABLE optimization_outbox (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    policy_version BIGINT NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_optimization_outbox_scope ON optimization_outbox(tenant_id,plan_id,status,created_at);
CREATE INDEX idx_optimization_outbox_retention ON optimization_outbox(status,updated_at);
