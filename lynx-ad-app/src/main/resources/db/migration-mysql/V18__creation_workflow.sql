CREATE TABLE delivery_creation (
  plan_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  revision INT NOT NULL DEFAULT 1,
  state_json LONGTEXT NOT NULL,
  work_pending INT NOT NULL DEFAULT 0,
  lease_until TIMESTAMP(6) NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  INDEX idx_creation_tenant (tenant_id)
);
CREATE TABLE delivery_publish_attempt (
  id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  plan_id VARCHAR(64) NOT NULL,
  revision INT NOT NULL,
  request_key VARCHAR(64) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  payload_json LONGTEXT NOT NULL,
  applied INT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL,
  qc_plan_id VARCHAR(64) NULL,
  error_message VARCHAR(500) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  UNIQUE KEY uk_publish_key (tenant_id, request_key),
  UNIQUE KEY uk_publish_revision (tenant_id, plan_id, revision)
);
CREATE TABLE creative_carousel_platform (
  tenant_id VARCHAR(64) NOT NULL,
  carousel_id VARCHAR(64) NOT NULL,
  account_id VARCHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL,
  qc_carousel_id VARCHAR(64) NULL,
  error_message VARCHAR(500) NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (tenant_id, carousel_id, account_id)
);
