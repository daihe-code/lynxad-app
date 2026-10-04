ALTER TABLE agent_action
  ADD COLUMN recovered_at DATETIME(3);

ALTER TABLE agent_action
  MODIFY COLUMN idempotency_key VARCHAR(128) NULL;

CREATE INDEX idx_agent_action_open
  ON agent_action (tenant_id, ad_plan_id, action_type);
