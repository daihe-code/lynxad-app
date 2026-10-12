CREATE TABLE oauth_application (
  id VARCHAR(36) PRIMARY KEY,
  platform VARCHAR(24) NOT NULL,
  app_id VARCHAR(64) NOT NULL,
  secret_enc TEXT NOT NULL,
  redirect_uri VARCHAR(1024) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE (platform, app_id)
) ENGINE=InnoDB;

CREATE TABLE oauth_grant (
  id VARCHAR(36) PRIMARY KEY,
  tenant_id VARCHAR(36) NOT NULL,
  application_id VARCHAR(36) NOT NULL,
  platform VARCHAR(24) NOT NULL,
  access_token_enc TEXT NOT NULL,
  refresh_token_enc TEXT,
  token_expires_at DATETIME(3) NOT NULL,
  refresh_expires_at DATETIME(3),
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  version BIGINT NOT NULL DEFAULT 0,
  refresh_owner VARCHAR(36),
  refresh_locked_until DATETIME(3),
  created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB;
CREATE INDEX idx_oauth_grant_tenant ON oauth_grant (tenant_id, platform);

CREATE TABLE oauth_authorization_session (
  id VARCHAR(36) PRIMARY KEY,
  tenant_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(36) NOT NULL,
  application_id VARCHAR(36) NOT NULL,
  state_hash VARCHAR(64) NOT NULL UNIQUE,
  auth_code_hash VARCHAR(64) UNIQUE,
  grant_id VARCHAR(36),
  status VARCHAR(32) NOT NULL,
  catalog_json LONGTEXT,
  selected_json LONGTEXT,
  error_message VARCHAR(256),
  return_path VARCHAR(128) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB;
CREATE INDEX idx_oauth_session_tenant ON oauth_authorization_session (tenant_id, user_id);

ALTER TABLE oauth_account ADD COLUMN grant_id VARCHAR(36);
ALTER TABLE oauth_account ADD COLUMN application_id VARCHAR(36);
ALTER TABLE oauth_account ADD COLUMN catalog_error VARCHAR(256);
CREATE INDEX idx_oauth_account_grant ON oauth_account (grant_id, status);
