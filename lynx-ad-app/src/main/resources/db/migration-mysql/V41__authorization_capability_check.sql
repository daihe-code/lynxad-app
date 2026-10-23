CREATE TABLE oauth_capability_check (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, account_id VARCHAR(36) NOT NULL,
 grant_id VARCHAR(36), account_status VARCHAR(24) NOT NULL, material_status VARCHAR(24) NOT NULL,
 source VARCHAR(64) NOT NULL, checked_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_oauth_capability_account ON oauth_capability_check(tenant_id,account_id,checked_at);
