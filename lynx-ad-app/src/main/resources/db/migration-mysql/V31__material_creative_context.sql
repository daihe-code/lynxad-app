CREATE TABLE IF NOT EXISTS material_creative_context (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL,
 plan_id VARCHAR(64) NOT NULL, account_id VARCHAR(64) NOT NULL, product_id VARCHAR(64) NOT NULL,
 material_id VARCHAR(64) NOT NULL, asset_key VARCHAR(256) NOT NULL,
 status VARCHAR(32) NOT NULL, body_json LONGTEXT NOT NULL, profile_json LONGTEXT,
 removed BOOLEAN NOT NULL DEFAULT FALSE, removal_reason VARCHAR(32),
 request_version INTEGER NOT NULL DEFAULT 1, attempts INTEGER NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(3) NOT NULL, lease_until DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 assessment_observed_at DATETIME(3) NULL,
 INDEX idx_material_context_scope(tenant_id,account_id,product_id,created_at),
 INDEX idx_material_context_pending(status,next_attempt_at,lease_until),
 INDEX idx_material_context_asset(tenant_id,asset_key,product_id)
);
