SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE tenant_legal_profile ADD COLUMN contact_name VARCHAR(64)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tenant_legal_profile' AND column_name = 'contact_name');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE tenant_legal_profile ADD COLUMN industry VARCHAR(100)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tenant_legal_profile' AND column_name = 'industry');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE tenant_legal_profile ADD COLUMN email VARCHAR(200)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tenant_legal_profile' AND column_name = 'email');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE tenant_legal_profile ADD COLUMN company_scale VARCHAR(64)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tenant_legal_profile' AND column_name = 'company_scale');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE tenant_legal_profile ADD COLUMN contact_user_id VARCHAR(36)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tenant_legal_profile' AND column_name = 'contact_user_id');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

CREATE TABLE IF NOT EXISTS tenant_qualification_review (
 id VARCHAR(36) PRIMARY KEY,
 tenant_id VARCHAR(36) NOT NULL,
 submitted_revision INTEGER NOT NULL,
 decision VARCHAR(24) NOT NULL,
 reason VARCHAR(512) NOT NULL,
 admin_id VARCHAR(36) NOT NULL,
 reviewed_at DATETIME(3) NOT NULL,
 UNIQUE(tenant_id, submitted_revision),
 INDEX idx_qualification_review_tenant (tenant_id, reviewed_at)
);
