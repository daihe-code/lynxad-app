-- Rollout: migrate first, drain plan edits, update BOTH API instances, then publish the new Web.
-- Older APIs do not honor these claims; mixed API versions must not accept plan edits.
-- Short durable claims protect platform writes across App instances and process restarts.
CREATE TABLE delivery_plan_settings_edit (
  tenant_id VARCHAR(64) NOT NULL, plan_id VARCHAR(64) NOT NULL,
  revision BIGINT NOT NULL DEFAULT 0, status VARCHAR(24) NOT NULL DEFAULT 'IDLE',
  request_key VARCHAR(64), root_plan_id VARCHAR(64), request_json TEXT,
  verification_key VARCHAR(64), started_at TIMESTAMP, updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (tenant_id, plan_id)
);
