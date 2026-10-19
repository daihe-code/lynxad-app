ALTER TABLE tenant_legal_profile ADD COLUMN contact_name VARCHAR(64);
ALTER TABLE tenant_legal_profile ADD COLUMN industry VARCHAR(100);
ALTER TABLE tenant_legal_profile ADD COLUMN email VARCHAR(200);
ALTER TABLE tenant_legal_profile ADD COLUMN company_scale VARCHAR(64);
ALTER TABLE tenant_legal_profile ADD COLUMN contact_user_id VARCHAR(36);
CREATE TABLE tenant_qualification_review (
 id VARCHAR(36) PRIMARY KEY,
 tenant_id VARCHAR(36) NOT NULL,
 submitted_revision INTEGER NOT NULL,
 decision VARCHAR(24) NOT NULL,
 reason VARCHAR(512) NOT NULL,
 admin_id VARCHAR(36) NOT NULL,
 reviewed_at TIMESTAMPTZ NOT NULL,
 UNIQUE(tenant_id, submitted_revision)
);
CREATE INDEX idx_qualification_review_tenant ON tenant_qualification_review(tenant_id, reviewed_at);
