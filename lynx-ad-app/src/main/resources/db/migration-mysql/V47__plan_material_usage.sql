CREATE TABLE qc_plan_material_usage (
    plan_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    advertiser_id VARCHAR(64) NOT NULL,
    qc_plan_id VARCHAR(64) NOT NULL,
    bindings_json TEXT,
    last_success_at TIMESTAMP,
    last_attempt_ok BOOLEAN NOT NULL DEFAULT FALSE,
    next_sync_at TIMESTAMP NOT NULL,
    lease_until TIMESTAMP,
    lease_token VARCHAR(36)
);
CREATE INDEX ix_plan_material_usage_due ON qc_plan_material_usage(next_sync_at);
CREATE INDEX ix_plan_material_usage_scope ON qc_plan_material_usage(tenant_id, account_id);
