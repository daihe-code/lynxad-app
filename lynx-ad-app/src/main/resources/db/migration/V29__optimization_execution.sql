ALTER TABLE optimization_policy ADD COLUMN hold_reason VARCHAR(500);
ALTER TABLE optimization_policy ADD COLUMN baseline_json TEXT;
ALTER TABLE optimization_policy ADD COLUMN last_action_at TIMESTAMPTZ;
ALTER TABLE optimization_policy ADD COLUMN auto_paused_reason VARCHAR(32);
ALTER TABLE optimization_policy ADD COLUMN auto_paused_at TIMESTAMPTZ;
CREATE TABLE optimization_account_guard (
 tenant_id VARCHAR(64) NOT NULL, account_id VARCHAR(64) NOT NULL,
 budget_limit DECIMAL(18,2), lease_owner VARCHAR(64), lease_until TIMESTAMPTZ,
 PRIMARY KEY(tenant_id,account_id)
);
CREATE TABLE optimization_event (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, plan_id VARCHAR(64) NOT NULL,
 account_id VARCHAR(64) NOT NULL, plan_name VARCHAR(255) NOT NULL,
 fingerprint VARCHAR(64) NOT NULL, policy_version BIGINT NOT NULL,
 kind VARCHAR(24), title VARCHAR(255) NOT NULL, evidence TEXT, proposal_json TEXT,
 report_json TEXT NOT NULL, status VARCHAR(32) NOT NULL,
 error_message VARCHAR(500), actual_json TEXT, created_at TIMESTAMPTZ NOT NULL,
 dispatched_at TIMESTAMPTZ, finished_at TIMESTAMPTZ,
 UNIQUE(tenant_id,fingerprint)
);
CREATE INDEX idx_optimization_event_history ON optimization_event(tenant_id,created_at);
CREATE INDEX idx_optimization_event_pending ON optimization_event(status,created_at);
CREATE INDEX idx_optimization_event_account ON optimization_event(tenant_id,account_id,status);
