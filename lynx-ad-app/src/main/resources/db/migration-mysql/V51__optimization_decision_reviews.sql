ALTER TABLE optimization_event ADD COLUMN scope_key VARCHAR(180);
ALTER TABLE optimization_event ADD COLUMN updated_at TIMESTAMP(6) NULL;
CREATE INDEX idx_optimization_event_scope ON optimization_event(tenant_id,plan_id,scope_key,status);
CREATE TABLE optimization_review (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, plan_id VARCHAR(64) NOT NULL,
 event_id VARCHAR(36) NOT NULL, content_work_id VARCHAR(36), policy_version BIGINT NOT NULL, workflow VARCHAR(24) NOT NULL,
 status VARCHAR(32) NOT NULL, before_json LONGTEXT NOT NULL, after_json LONGTEXT,
 baseline_json LONGTEXT, result_json LONGTEXT, strategy_json LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, action_at TIMESTAMP(6) NULL, updated_at TIMESTAMP(6) NOT NULL,
 next_check_at TIMESTAMP(6) NOT NULL, lease_owner VARCHAR(64), lease_until TIMESTAMP(6) NULL,
 UNIQUE(tenant_id,event_id)
);
CREATE INDEX idx_optimization_review_due ON optimization_review(status,next_check_at);
CREATE INDEX idx_optimization_review_plan ON optimization_review(tenant_id,plan_id,created_at);
CREATE TABLE optimization_confirmation (
 event_id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, plan_id VARCHAR(64) NOT NULL,
 policy_version BIGINT NOT NULL, actor_id VARCHAR(64) NOT NULL, facts_json LONGTEXT NOT NULL,
 action_json LONGTEXT NOT NULL, report_json LONGTEXT NOT NULL, confirmed_at TIMESTAMP(6) NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL, addition DECIMAL(18,2) NOT NULL DEFAULT 0,
 day_key VARCHAR(10) NOT NULL, status VARCHAR(24) NOT NULL
);
CREATE INDEX idx_optimization_confirmation_day ON optimization_confirmation(tenant_id,plan_id,day_key,status);
