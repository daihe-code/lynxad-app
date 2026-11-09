CREATE TABLE workspace_child_invite (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, phone VARCHAR(32) NOT NULL,
 member_role VARCHAR(16) NOT NULL, token_hash VARCHAR(64) NOT NULL UNIQUE,
 status VARCHAR(16) NOT NULL, expires_at TIMESTAMP NOT NULL,
 created_by VARCHAR(36) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE (tenant_id,phone)
);
CREATE INDEX idx_child_invite_quota ON workspace_child_invite(tenant_id,status,expires_at);

CREATE TABLE workspace_team_event (
 id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, actor_user_id VARCHAR(36) NOT NULL,
 target_id VARCHAR(36) NOT NULL, event_type VARCHAR(24) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workspace_point_order (
 order_id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, actor_user_id VARCHAR(36) NOT NULL,
 sku VARCHAR(32) NOT NULL, points BIGINT NOT NULL
);

CREATE TABLE workspace_generation_charge (
 tenant_id VARCHAR(36) NOT NULL, task_key VARCHAR(128) NOT NULL,
 rates_json TEXT NOT NULL, PRIMARY KEY(tenant_id,task_key)
);
