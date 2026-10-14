CREATE TABLE optimization_policy (
    tenant_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    policy_version BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    config_json LONGTEXT NOT NULL,
    next_check_at TIMESTAMP(6) NOT NULL,
    lease_owner VARCHAR(64),
    lease_until TIMESTAMP(6),
    last_check_at TIMESTAMP(6),
    last_success_at TIMESTAMP(6),
    last_error VARCHAR(500),
    result_json LONGTEXT,
    PRIMARY KEY (tenant_id,plan_id),
    INDEX idx_optimization_policy_due(enabled,next_check_at,lease_until)
);
CREATE TABLE product_economics_profile (
    tenant_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    aweme_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    contribution_margin_rate DECIMAL(12,8) NOT NULL,
    refund_reserve_rate DECIMAL(12,8) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id,account_id,aweme_id,product_id)
);
CREATE TABLE optimization_outbox (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    policy_version BIGINT NOT NULL,
    payload_json LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    INDEX idx_optimization_outbox_scope(tenant_id,plan_id,status,created_at),
    INDEX idx_optimization_outbox_retention(status,updated_at)
);
