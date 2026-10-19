CREATE TABLE IF NOT EXISTS platform_material_catalog (
  tenant_id VARCHAR(36) NOT NULL,
  account_id VARCHAR(36) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  material_type VARCHAR(16) NOT NULL,
  entry_key VARCHAR(130) NOT NULL,
  object_id VARCHAR(128) NOT NULL,
  material_id VARCHAR(128),
  filename VARCHAR(512) NOT NULL,
  size_bytes BIGINT NOT NULL,
  width INTEGER,
  height INTEGER,
  duration_sec INTEGER,
  image_mode VARCHAR(64),
  review_badge VARCHAR(32),
  created_at TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (tenant_id, account_id, material_type, entry_key),
  INDEX idx_platform_material_catalog_tenant(tenant_id, material_type, created_at)
);
CREATE TABLE IF NOT EXISTS platform_material_sync (
  account_id VARCHAR(36) PRIMARY KEY,
  tenant_id VARCHAR(36) NOT NULL,
  next_sync_at TIMESTAMP(3) NOT NULL,
  lease_until TIMESTAMP(3) NULL,
  last_success_at TIMESTAMP(3) NULL,
  last_attempt_ok BOOLEAN,
  INDEX idx_platform_material_sync_due(next_sync_at, lease_until)
);
