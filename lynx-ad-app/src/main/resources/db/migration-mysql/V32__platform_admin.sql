CREATE TABLE platform_admin_account (
  id VARCHAR(36) PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  display_name VARCHAR(64) NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  failed_count INTEGER NOT NULL DEFAULT 0,
  locked_until DATETIME(3),
  last_login_at DATETIME(3),
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE platform_admin_session (
  id VARCHAR(64) PRIMARY KEY,
  admin_id VARCHAR(36) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  revoked_at DATETIME(3),
  created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB;
CREATE INDEX idx_platform_admin_session_account ON platform_admin_session(admin_id, expires_at);

CREATE TABLE platform_admin_audit (
  id VARCHAR(36) PRIMARY KEY,
  admin_id VARCHAR(36),
  actor_name VARCHAR(64) NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  result VARCHAR(16) NOT NULL,
  target_user_id VARCHAR(36),
  tenant_id VARCHAR(36),
  description VARCHAR(512) NOT NULL,
  ip VARCHAR(64),
  user_agent VARCHAR(512),
  created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB;
CREATE INDEX idx_platform_admin_audit_time ON platform_admin_audit(created_at, id);

CREATE TABLE user_login_record (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36),
  tenant_id VARCHAR(36),
  phone VARCHAR(20),
  login_method VARCHAR(16) NOT NULL,
  result VARCHAR(16) NOT NULL,
  failure_reason VARCHAR(128),
  ip VARCHAR(64),
  user_agent VARCHAR(512),
  created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB;
CREATE INDEX idx_user_login_user_time ON user_login_record(user_id, created_at, id);
CREATE INDEX idx_user_login_time ON user_login_record(created_at, id);
CREATE INDEX idx_user_login_phone ON user_login_record(phone, created_at);
INSERT INTO user_login_record(id,user_id,tenant_id,phone,login_method,result,ip,user_agent,created_at)
SELECT a.id,a.user_id,a.tenant_id,u.phone,'LEGACY',a.result,a.ip,a.user_agent,a.created_at
FROM audit_record a LEFT JOIN sys_user u ON u.id=a.user_id WHERE a.action_type='LOGIN';

CREATE TABLE platform_package_adjustment (
  request_id VARCHAR(36) PRIMARY KEY,
  request_hash VARCHAR(64) NOT NULL,
  admin_id VARCHAR(36) NOT NULL,
  actor_name VARCHAR(64) NOT NULL,
  user_id VARCHAR(36) NOT NULL,
  tenant_id VARCHAR(36) NOT NULL,
  grant_type VARCHAR(24) NOT NULL,
  plan_code VARCHAR(32),
  days INTEGER NOT NULL,
  before_end DATETIME(3),
  after_end DATETIME(3) NOT NULL,
  reason VARCHAR(300) NOT NULL,
  created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB;
CREATE INDEX idx_platform_package_adjustment_user ON platform_package_adjustment(user_id, created_at);
CREATE INDEX idx_platform_package_adjustment_tenant ON platform_package_adjustment(tenant_id, created_at);
