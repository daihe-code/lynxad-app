SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE oauth_account ADD COLUMN authorized_by_user_id VARCHAR(36)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'oauth_account' AND column_name = 'authorized_by_user_id');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

CREATE TABLE IF NOT EXISTS oauth_auth_code_audit (
  id              VARCHAR(36) PRIMARY KEY,
  tenant_id       VARCHAR(36) NOT NULL,
  user_id         VARCHAR(36) NOT NULL,
  auth_code_hash  VARCHAR(64) NOT NULL,
  advertiser_id   VARCHAR(32),
  status          VARCHAR(16) NOT NULL,
  created_at      DATETIME(3) NOT NULL,
  used_at         DATETIME(3),
  INDEX idx_oauth_auth_code_hash (auth_code_hash)
) ENGINE=InnoDB;
