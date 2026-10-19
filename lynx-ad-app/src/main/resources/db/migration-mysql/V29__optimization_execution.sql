SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE optimization_policy ADD COLUMN hold_reason VARCHAR(500)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'optimization_policy' AND column_name = 'hold_reason');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE optimization_policy ADD COLUMN baseline_json LONGTEXT', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'optimization_policy' AND column_name = 'baseline_json');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE optimization_policy ADD COLUMN last_action_at DATETIME(3) NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'optimization_policy' AND column_name = 'last_action_at');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE optimization_policy ADD COLUMN auto_paused_reason VARCHAR(32)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'optimization_policy' AND column_name = 'auto_paused_reason');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE optimization_policy ADD COLUMN auto_paused_at DATETIME(3) NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'optimization_policy' AND column_name = 'auto_paused_at');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

CREATE TABLE IF NOT EXISTS optimization_account_guard (
 tenant_id VARCHAR(64) NOT NULL, account_id VARCHAR(64) NOT NULL,
 budget_limit DECIMAL(18,2), lease_owner VARCHAR(64), lease_until DATETIME(3) NULL,
 PRIMARY KEY(tenant_id,account_id)
);
CREATE TABLE IF NOT EXISTS optimization_event (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, plan_id VARCHAR(64) NOT NULL,
 account_id VARCHAR(64) NOT NULL, plan_name VARCHAR(255) NOT NULL,
 fingerprint VARCHAR(64) NOT NULL, policy_version BIGINT NOT NULL,
 kind VARCHAR(24), title VARCHAR(255) NOT NULL, evidence LONGTEXT, proposal_json LONGTEXT,
 report_json LONGTEXT NOT NULL, status VARCHAR(32) NOT NULL,
 error_message VARCHAR(500), actual_json LONGTEXT, created_at DATETIME(3) NOT NULL,
 dispatched_at DATETIME(3) NULL, finished_at DATETIME(3) NULL,
 UNIQUE(tenant_id,fingerprint),
 INDEX idx_optimization_event_history(tenant_id,created_at),
 INDEX idx_optimization_event_pending(status,created_at),
 INDEX idx_optimization_event_account(tenant_id,account_id,status)
);
