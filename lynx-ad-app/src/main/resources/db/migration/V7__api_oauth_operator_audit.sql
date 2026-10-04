ALTER TABLE oauth_account
  ADD COLUMN authorized_by_user_id VARCHAR(36);

CREATE TABLE oauth_auth_code_audit (
  id              VARCHAR(36) PRIMARY KEY,
  tenant_id       VARCHAR(36) NOT NULL,
  user_id         VARCHAR(36) NOT NULL,
  auth_code_hash  VARCHAR(64) NOT NULL,
  advertiser_id   VARCHAR(32),
  status          VARCHAR(16) NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL,
  used_at         TIMESTAMPTZ
);

CREATE INDEX idx_oauth_auth_code_hash ON oauth_auth_code_audit (auth_code_hash);
