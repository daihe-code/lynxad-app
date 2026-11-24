CREATE TABLE delivery_transfer_rebuild (
  tenant_id VARCHAR(64) NOT NULL,
  request_key VARCHAR(64) NOT NULL,
  oauth_account_id VARCHAR(64) NOT NULL,
  source_qc_id VARCHAR(64) NOT NULL,
  source_payload TEXT NOT NULL,
  source_detail TEXT NOT NULL,
  source_report TEXT,
  target_name VARCHAR(120) NOT NULL,
  target_platform_name VARCHAR(120) NOT NULL,
  restore_platform_name VARCHAR(120) NOT NULL,
  restore_qc_id VARCHAR(64),
  PRIMARY KEY (tenant_id, request_key)
);

CREATE TABLE delivery_transfer_history (
  tenant_id VARCHAR(64) NOT NULL,
  oauth_account_id VARCHAR(64) NOT NULL,
  qc_plan_id VARCHAR(64) NOT NULL,
  plan_id VARCHAR(64) NOT NULL,
  archived_at TIMESTAMP NOT NULL,
  snapshot TEXT NOT NULL,
  PRIMARY KEY (tenant_id, oauth_account_id, qc_plan_id)
);
CREATE INDEX idx_transfer_history_plan ON delivery_transfer_history(tenant_id, plan_id, archived_at);
