CREATE TABLE material_creative_context (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL,
 plan_id VARCHAR(64) NOT NULL, account_id VARCHAR(64) NOT NULL, product_id VARCHAR(64) NOT NULL,
 material_id VARCHAR(64) NOT NULL, asset_key VARCHAR(256) NOT NULL,
 status VARCHAR(32) NOT NULL, body_json TEXT NOT NULL, profile_json TEXT,
 removed BOOLEAN NOT NULL DEFAULT FALSE, removal_reason VARCHAR(32),
 request_version INTEGER NOT NULL DEFAULT 1, attempts INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMP NOT NULL, lease_until TIMESTAMP,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 assessment_observed_at TIMESTAMP
);
CREATE INDEX idx_material_context_scope ON material_creative_context(tenant_id,account_id,product_id,created_at);
CREATE INDEX idx_material_context_pending ON material_creative_context(status,next_attempt_at,lease_until);
CREATE INDEX idx_material_context_asset ON material_creative_context(tenant_id,asset_key,product_id);
