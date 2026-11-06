-- V1.3 account model. Keep existing tenant/account/resource identifiers intact.
ALTER TABLE tenant_member ADD COLUMN membership_id VARCHAR(64);
ALTER TABLE tenant_member ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE tenant_member ADD COLUMN permission_version BIGINT NOT NULL DEFAULT 1;
UPDATE tenant_member SET membership_id=md5(tenant_id || ':' || user_id);
CREATE UNIQUE INDEX uk_membership_id ON tenant_member(membership_id);

CREATE TABLE client_space (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, name VARCHAR(128) NOT NULL,
 is_default BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(tenant_id,name)
);
INSERT INTO client_space(id,tenant_id,name,is_default) SELECT id,id,'我的业务',TRUE FROM sys_tenant;

CREATE TABLE real_ad_account (
 id VARCHAR(64) PRIMARY KEY, platform VARCHAR(24) NOT NULL, id_namespace VARCHAR(32) NOT NULL,
 advertiser_id VARCHAR(64) NOT NULL, UNIQUE(platform,id_namespace,advertiser_id)
);
CREATE TABLE ad_account_binding (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, real_account_id VARCHAR(64) NOT NULL,
 bound_user_id VARCHAR(36), client_space_id VARCHAR(36), status VARCHAR(24) NOT NULL,
 binding_version BIGINT NOT NULL DEFAULT 1, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(real_account_id) REFERENCES real_ad_account(id)
);
CREATE INDEX idx_binding_tenant ON ad_account_binding(tenant_id,status);
-- Only this table owns the exclusive current claim. History stays in binding.
CREATE TABLE ad_account_claim (
 real_account_id VARCHAR(64) PRIMARY KEY, binding_id VARCHAR(36) NOT NULL UNIQUE,
 tenant_id VARCHAR(36) NOT NULL, bound_user_id VARCHAR(36) NOT NULL,
 FOREIGN KEY(real_account_id) REFERENCES real_ad_account(id),
 FOREIGN KEY(binding_id) REFERENCES ad_account_binding(id)
);
CREATE TABLE account_grant_path (
 binding_id VARCHAR(36) NOT NULL, grant_id VARCHAR(36) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(binding_id,grant_id), FOREIGN KEY(binding_id) REFERENCES ad_account_binding(id)
);

INSERT INTO real_ad_account(id,platform,id_namespace,advertiser_id)
 SELECT md5(platform || ':advertiser:' || advertiser_id),platform,'advertiser',advertiser_id
 FROM oauth_account GROUP BY platform,advertiser_id;
INSERT INTO ad_account_binding(id,tenant_id,real_account_id,bound_user_id,client_space_id,status)
 SELECT a.id,a.tenant_id,md5(a.platform || ':advertiser:' || a.advertiser_id),
 COALESCE(a.authorized_by_user_id,(SELECT MIN(m.user_id) FROM tenant_member m WHERE m.tenant_id=a.tenant_id AND m.member_role='OWNER' HAVING COUNT(*)=1)),a.tenant_id,
 CASE WHEN a.status IN ('UNBOUND','DELETED') THEN 'RELEASED'
 WHEN (SELECT COUNT(*) FROM oauth_account b WHERE b.platform=a.platform AND b.advertiser_id=a.advertiser_id AND b.status NOT IN ('UNBOUND','DELETED'))>1 THEN 'REVIEW_REQUIRED'
 WHEN COALESCE(a.authorized_by_user_id,(SELECT MIN(m.user_id) FROM tenant_member m WHERE m.tenant_id=a.tenant_id AND m.member_role='OWNER' HAVING COUNT(*)=1)) IS NULL THEN 'REVIEW_REQUIRED'
 ELSE 'ACTIVE' END
 FROM oauth_account a;
UPDATE ad_account_binding SET status='REVIEW_REQUIRED'
 WHERE status='ACTIVE' AND NOT EXISTS (SELECT 1 FROM tenant_member m JOIN sys_user u ON u.id=m.user_id
 WHERE m.tenant_id=ad_account_binding.tenant_id AND m.user_id=ad_account_binding.bound_user_id
 AND m.status='ACTIVE' AND u.status='ACTIVE' AND m.member_role IN ('OWNER','MEMBER','OPERATOR'));
INSERT INTO ad_account_claim(real_account_id,binding_id,tenant_id,bound_user_id)
 SELECT real_account_id,id,tenant_id,bound_user_id FROM ad_account_binding WHERE status='ACTIVE';
INSERT INTO account_grant_path(binding_id,grant_id) SELECT id,grant_id FROM oauth_account WHERE grant_id IS NOT NULL;

CREATE TABLE point_wallet (
 tenant_id VARCHAR(36) PRIMARY KEY, available NUMERIC(20,4) NOT NULL DEFAULT 0,
 frozen NUMERIC(20,4) NOT NULL DEFAULT 0, consumed NUMERIC(20,4) NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 1,
 CHECK(available>=0 AND frozen>=0 AND consumed>=0)
);
INSERT INTO point_wallet(tenant_id) SELECT id FROM sys_tenant;
CREATE TABLE point_reservation (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, task_key VARCHAR(128) NOT NULL,
 binding_id VARCHAR(36), actor_user_id VARCHAR(36), amount NUMERIC(20,4) NOT NULL,
 settled NUMERIC(20,4), status VARCHAR(16) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(tenant_id,task_key), CHECK(amount>0), FOREIGN KEY(tenant_id) REFERENCES point_wallet(tenant_id)
);
CREATE TABLE point_ledger (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, event_key VARCHAR(160) NOT NULL,
 event_type VARCHAR(16) NOT NULL, amount NUMERIC(20,4) NOT NULL,
 reservation_id VARCHAR(36), binding_id VARCHAR(36), actor_user_id VARCHAR(36),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id,event_key)
);
CREATE INDEX idx_point_ledger_tenant ON point_ledger(tenant_id,created_at);
CREATE TABLE workspace_session_revocation (
 session_id VARCHAR(64) PRIMARY KEY, expires_at TIMESTAMP NOT NULL
);
CREATE TABLE workspace_engine_scope (
 tenant_id VARCHAR(36) NOT NULL, task_id VARCHAR(128) NOT NULL,
 binding_id VARCHAR(36) NOT NULL, scope_json TEXT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(tenant_id,task_id)
);
CREATE TABLE workspace_publish_scope (
 tenant_id VARCHAR(36) NOT NULL, request_key VARCHAR(64) NOT NULL,
 scope_json TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(tenant_id,request_key)
);
