-- The plan repository writes review outcomes and these events in one transaction.
-- Old APIs still send notifications directly; drain old writers before rolling out.
CREATE TABLE delivery_plan_audit_notice (
  tenant_id VARCHAR(64) NOT NULL, plan_id VARCHAR(64) NOT NULL,
  qc_plan_id VARCHAR(64) NOT NULL, cycle_id VARCHAR(36) NOT NULL,
  state VARCHAR(16) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (tenant_id,plan_id)
);
CREATE TABLE inbox_outbox (
  id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL,
  event_key VARCHAR(64) NOT NULL, notification_id VARCHAR(36) NOT NULL,
  title VARCHAR(128) NOT NULL, body TEXT, biz_type VARCHAR(32), biz_id VARCHAR(64),
  occurred_at TIMESTAMP(3) NOT NULL, state VARCHAR(16) NOT NULL,
  attempts INT NOT NULL DEFAULT 0, next_attempt_at TIMESTAMP(3) NOT NULL, last_attempt_at TIMESTAMP(3),
  lease_key VARCHAR(36), lease_until TIMESTAMP(3), delivered_at TIMESTAMP(3),
  last_failure VARCHAR(32),
  UNIQUE (tenant_id,event_key), UNIQUE (notification_id)
);
CREATE INDEX idx_inbox_outbox_due ON inbox_outbox(state,next_attempt_at);
CREATE INDEX idx_inbox_outbox_tenant_due ON inbox_outbox(tenant_id,state,next_attempt_at);
