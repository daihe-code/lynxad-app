SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE agent_action ADD COLUMN recovered_at DATETIME(3)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'agent_action' AND column_name = 'recovered_at');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

ALTER TABLE agent_action
  MODIFY COLUMN idempotency_key VARCHAR(128) NULL;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'CREATE INDEX idx_agent_action_open ON agent_action (tenant_id, ad_plan_id, action_type)', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'agent_action' AND index_name = 'idx_agent_action_open');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;
