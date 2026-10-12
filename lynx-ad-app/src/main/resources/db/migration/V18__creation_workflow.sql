CREATE TABLE delivery_creation (
  plan_id VARCHAR(64) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL,
  revision INT NOT NULL DEFAULT 1, state_json TEXT NOT NULL, work_pending INT NOT NULL DEFAULT 0,
  lease_until TIMESTAMP NULL, updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_creation_tenant ON delivery_creation(tenant_id);
CREATE TABLE delivery_publish_attempt (
  id VARCHAR(64) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL,
  plan_id VARCHAR(64) NOT NULL, revision INT NOT NULL,
  request_key VARCHAR(64) NOT NULL, payload_hash VARCHAR(64) NOT NULL,
  payload_json TEXT NOT NULL, applied INT NOT NULL DEFAULT 0, status VARCHAR(24) NOT NULL,
  qc_plan_id VARCHAR(64), error_message VARCHAR(500),
  created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
  UNIQUE (tenant_id, request_key), UNIQUE (tenant_id, plan_id, revision)
);
CREATE TABLE creative_carousel_platform (
  tenant_id VARCHAR(64) NOT NULL, carousel_id VARCHAR(64) NOT NULL,
  account_id VARCHAR(64) NOT NULL, status VARCHAR(24) NOT NULL,
  qc_carousel_id VARCHAR(64), error_message VARCHAR(500), updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (tenant_id, carousel_id, account_id)
);
