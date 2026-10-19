SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE user_profile ADD COLUMN company_scale VARCHAR(64)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'user_profile' AND column_name = 'company_scale');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

CREATE TABLE IF NOT EXISTS audit_record (
  id            VARCHAR(36) PRIMARY KEY,
  tenant_id     VARCHAR(36),
  user_id       VARCHAR(36),
  actor_name    VARCHAR(64)  NOT NULL,
  action_type   VARCHAR(32)  NOT NULL,
  object_type   VARCHAR(32),
  object_id     VARCHAR(64),
  object_label  VARCHAR(256),
  result        VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS',
  ip            VARCHAR(64),
  session_id    VARCHAR(64),
  user_agent    VARCHAR(512),
  extra         JSON,
  created_at    DATETIME(3)  NOT NULL DEFAULT (CURRENT_TIMESTAMP(3)),
  INDEX idx_audit_tenant_time (tenant_id, created_at DESC),
  INDEX idx_audit_type_time (action_type, created_at DESC)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS contact_ticket (
  id          VARCHAR(36) PRIMARY KEY,
  tenant_id   VARCHAR(36),
  user_id     VARCHAR(36),
  phone       VARCHAR(20)  NOT NULL,
  source      VARCHAR(32)  NOT NULL DEFAULT 'LANDING',
  status      VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
  created_at  DATETIME(3)  NOT NULL DEFAULT (CURRENT_TIMESTAMP(3)),
  INDEX idx_contact_ticket_created (created_at DESC)
) ENGINE=InnoDB;
