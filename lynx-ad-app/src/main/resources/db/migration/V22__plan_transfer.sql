CREATE TABLE delivery_transfer_group (
  tenant_id VARCHAR(64) NOT NULL, root_plan_id VARCHAR(64) NOT NULL,
  active_plan_id VARCHAR(64) NOT NULL, conservative_plan_id VARCHAR(64), custom_plan_id VARCHAR(64),
  request_key VARCHAR(64), updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (tenant_id, root_plan_id)
);
CREATE TABLE delivery_transfer_attempt (
  tenant_id VARCHAR(64) NOT NULL, request_key VARCHAR(64) NOT NULL, root_plan_id VARCHAR(64) NOT NULL,
  source_plan_id VARCHAR(64) NOT NULL, target_plan_id VARCHAR(64) NOT NULL,
  request_json TEXT NOT NULL, payload_json TEXT NOT NULL,
  source_enabled INT NOT NULL, qc_plan_id VARCHAR(64),
  status VARCHAR(24) NOT NULL, phase VARCHAR(24) NOT NULL, error_message VARCHAR(500),
  created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (tenant_id, request_key)
);
CREATE INDEX idx_transfer_root ON delivery_transfer_attempt(tenant_id, root_plan_id);
