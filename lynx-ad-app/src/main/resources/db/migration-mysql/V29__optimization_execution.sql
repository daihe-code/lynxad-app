ALTER TABLE optimization_policy ADD COLUMN hold_reason VARCHAR(500);
ALTER TABLE optimization_policy ADD COLUMN baseline_json LONGTEXT;
ALTER TABLE optimization_policy ADD COLUMN last_action_at TIMESTAMP(6) NULL;
ALTER TABLE optimization_policy ADD COLUMN auto_paused_reason VARCHAR(32);
ALTER TABLE optimization_policy ADD COLUMN auto_paused_at TIMESTAMP(6) NULL;
CREATE TABLE optimization_account_guard (
 tenant_id VARCHAR(64) NOT NULL, account_id VARCHAR(64) NOT NULL,
 budget_limit DECIMAL(18,2), lease_owner VARCHAR(64), lease_until TIMESTAMP(6) NULL,
 PRIMARY KEY(tenant_id,account_id)
);
CREATE TABLE optimization_event (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, plan_id VARCHAR(64) NOT NULL,
 account_id VARCHAR(64) NOT NULL, plan_name VARCHAR(255) NOT NULL,
 fingerprint VARCHAR(64) NOT NULL, policy_version BIGINT NOT NULL,
 kind VARCHAR(24), title VARCHAR(255) NOT NULL, evidence LONGTEXT, proposal_json LONGTEXT,
 report_json LONGTEXT NOT NULL, status VARCHAR(32) NOT NULL,
 error_message VARCHAR(500), actual_json LONGTEXT, created_at TIMESTAMP(6) NOT NULL,
 dispatched_at TIMESTAMP(6) NULL, finished_at TIMESTAMP(6) NULL,
 UNIQUE(tenant_id,fingerprint),
 INDEX idx_optimization_event_history(tenant_id,created_at),
 INDEX idx_optimization_event_pending(status,created_at),
 INDEX idx_optimization_event_account(tenant_id,account_id,status)
);
