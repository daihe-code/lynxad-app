ALTER TABLE agent_action
  ADD COLUMN recovered_at TIMESTAMPTZ;

ALTER TABLE agent_action
  ALTER COLUMN idempotency_key TYPE VARCHAR(128);

CREATE INDEX idx_agent_action_open
  ON agent_action (tenant_id, ad_plan_id, action_type)
  WHERE recovered_at IS NULL AND result = 'ALERT';
