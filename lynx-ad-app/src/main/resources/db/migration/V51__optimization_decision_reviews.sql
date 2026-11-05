ALTER TABLE optimization_event ADD COLUMN scope_key VARCHAR(180);
ALTER TABLE optimization_event ADD COLUMN updated_at TIMESTAMPTZ;
CREATE INDEX idx_optimization_event_scope ON optimization_event(tenant_id,plan_id,scope_key,status);
CREATE TABLE optimization_review (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, plan_id VARCHAR(64) NOT NULL,
 event_id VARCHAR(36) NOT NULL, content_work_id VARCHAR(36), policy_version BIGINT NOT NULL, workflow VARCHAR(24) NOT NULL,
 status VARCHAR(32) NOT NULL, before_json TEXT NOT NULL, after_json TEXT,
 baseline_json TEXT, result_json TEXT, strategy_json TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, action_at TIMESTAMPTZ, updated_at TIMESTAMPTZ NOT NULL,
 next_check_at TIMESTAMPTZ NOT NULL, lease_owner VARCHAR(64), lease_until TIMESTAMPTZ,
 UNIQUE(tenant_id,event_id)
);
CREATE INDEX idx_optimization_review_due ON optimization_review(status,next_check_at);
CREATE INDEX idx_optimization_review_plan ON optimization_review(tenant_id,plan_id,created_at);
CREATE TABLE optimization_confirmation (
 event_id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, plan_id VARCHAR(64) NOT NULL,
 policy_version BIGINT NOT NULL, actor_id VARCHAR(64) NOT NULL, facts_json TEXT NOT NULL,
 action_json TEXT NOT NULL, report_json TEXT NOT NULL, confirmed_at TIMESTAMPTZ NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL, addition DECIMAL(18,2) NOT NULL DEFAULT 0,
 day_key VARCHAR(10) NOT NULL, status VARCHAR(24) NOT NULL
);
CREATE INDEX idx_optimization_confirmation_day ON optimization_confirmation(tenant_id,plan_id,day_key,status);
