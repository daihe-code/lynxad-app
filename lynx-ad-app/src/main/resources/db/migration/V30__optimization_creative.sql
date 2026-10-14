CREATE TABLE optimization_creative_job (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, plan_id VARCHAR(64) NOT NULL,
 logical_plan_id VARCHAR(64) NOT NULL, account_id VARCHAR(64) NOT NULL, product_id VARCHAR(64) NOT NULL,
 policy_version BIGINT NOT NULL, status VARCHAR(32) NOT NULL, reserved_cost DECIMAL(18,2) NOT NULL,
 body_json TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 published_at TIMESTAMPTZ, lease_owner VARCHAR(64), lease_until TIMESTAMPTZ
);
CREATE INDEX idx_optimization_creative_scope ON optimization_creative_job(tenant_id,logical_plan_id,product_id,created_at);
CREATE INDEX idx_optimization_creative_pending ON optimization_creative_job(status,lease_until);
