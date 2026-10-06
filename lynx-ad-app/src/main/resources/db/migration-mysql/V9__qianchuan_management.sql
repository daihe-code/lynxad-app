CREATE TABLE qc_plan_metadata (
  plan_id VARCHAR(36) PRIMARY KEY,
  tenant_id VARCHAR(36) NOT NULL,
  oauth_account_id VARCHAR(36) NOT NULL,
  qc_plan_id VARCHAR(32) NOT NULL,
  payload LONGTEXT NOT NULL,
  detail LONGTEXT,
  guarantee VARCHAR(64),
  today_report LONGTEXT,
  yesterday_report LONGTEXT,
  report_date DATE,
  report_ts DATETIME(3),
  synced_at DATETIME(3) NOT NULL,
  UNIQUE (tenant_id, oauth_account_id, qc_plan_id)
) ENGINE=InnoDB;

CREATE TABLE qc_control_submission (
  tenant_id VARCHAR(36) NOT NULL,
  request_key VARCHAR(64) NOT NULL,
  plan_id VARCHAR(36) NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  task_id VARCHAR(32),
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (tenant_id, request_key),
  UNIQUE (tenant_id, request_hash)
) ENGINE=InnoDB;
