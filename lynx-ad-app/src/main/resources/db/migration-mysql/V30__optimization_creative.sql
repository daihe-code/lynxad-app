CREATE TABLE optimization_creative_job (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, plan_id VARCHAR(64) NOT NULL,
 logical_plan_id VARCHAR(64) NOT NULL, account_id VARCHAR(64) NOT NULL, product_id VARCHAR(64) NOT NULL,
 policy_version BIGINT NOT NULL, status VARCHAR(32) NOT NULL, reserved_cost DECIMAL(18,2) NOT NULL,
 body_json LONGTEXT NOT NULL, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 published_at TIMESTAMP(3) NULL, lease_owner VARCHAR(64), lease_until TIMESTAMP(3) NULL,
 INDEX idx_optimization_creative_scope(tenant_id,logical_plan_id,product_id,created_at),
 INDEX idx_optimization_creative_pending(status,lease_until)
);
