-- LynxAd Flyway V1 for MySQL 8.0. Table names match the PostgreSQL scripts.

CREATE TABLE IF NOT EXISTS sys_tenant (
  id            VARCHAR(36) PRIMARY KEY,
  name          VARCHAR(128) NOT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  created_at    DATETIME(3)  NOT NULL DEFAULT (CURRENT_TIMESTAMP(3))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sys_user (
  id             VARCHAR(36) PRIMARY KEY,
  phone          VARCHAR(20) NOT NULL UNIQUE,
  password_hash  VARCHAR(128),
  status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at     DATETIME(3) NOT NULL DEFAULT (CURRENT_TIMESTAMP(3))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tenant_member (
  tenant_id    VARCHAR(36) NOT NULL REFERENCES sys_tenant(id),
  user_id      VARCHAR(36) NOT NULL REFERENCES sys_user(id),
  member_role  VARCHAR(16) NOT NULL,
  created_at   DATETIME(3) NOT NULL DEFAULT (CURRENT_TIMESTAMP(3)),
  PRIMARY KEY (tenant_id, user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_profile (
  user_id          VARCHAR(36) PRIMARY KEY REFERENCES sys_user(id),
  nickname         VARCHAR(64),
  email            VARCHAR(128),
  avatar_url       VARCHAR(512),
  company_name     VARCHAR(128),
  industry         VARCHAR(64),
  position         VARCHAR(64),
  monthly_budget   VARCHAR(64),
  ad_experience    VARCHAR(64),
  core_demand      VARCHAR(128),
  completed_at     DATETIME(3)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS oauth_account (
  id                   VARCHAR(36) PRIMARY KEY,
  tenant_id            VARCHAR(36) NOT NULL,
  platform             VARCHAR(16) NOT NULL,
  advertiser_id        VARCHAR(32) NOT NULL,
  account_name         VARCHAR(128),
  access_token_enc     TEXT NOT NULL,
  refresh_token_enc    TEXT,
  token_expires_at     DATETIME(3),
  refresh_expires_at   DATETIME(3),
  status               VARCHAR(16) NOT NULL,
  authorized_at        DATETIME(3),
  UNIQUE (tenant_id, platform, advertiser_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS oauth_aweme (
  id                VARCHAR(36) PRIMARY KEY,
  oauth_account_id  VARCHAR(36) NOT NULL REFERENCES oauth_account(id),
  aweme_id          VARCHAR(32) NOT NULL,
  name              VARCHAR(128),
  avatar            VARCHAR(512),
  aweme_type        VARCHAR(24) NOT NULL,
  is_primary        BOOLEAN NOT NULL DEFAULT false,
  UNIQUE (oauth_account_id, aweme_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS product (
  id               VARCHAR(36) PRIMARY KEY,
  tenant_id        VARCHAR(36) NOT NULL,
  aweme_id         VARCHAR(32) NOT NULL,
  qc_product_id    VARCHAR(32) NOT NULL,
  name             VARCHAR(256),
  price            DECIMAL(12,2),
  user_price       DECIMAL(12,2),
  image_url        VARCHAR(512),
  tags_json        JSON,
  analysis_json    JSON,
  potential_score  DECIMAL(6,2),
  gray_reason      VARCHAR(256),
  updated_at       DATETIME(3) NOT NULL DEFAULT (CURRENT_TIMESTAMP(3)),
  UNIQUE (tenant_id, aweme_id, qc_product_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ad_plan (
  id                 VARCHAR(36) PRIMARY KEY,
  tenant_id          VARCHAR(36) NOT NULL,
  oauth_account_id   VARCHAR(36),
  aweme_id           VARCHAR(32) NOT NULL,
  name               VARCHAR(30) NOT NULL,
  status             VARCHAR(24) NOT NULL,
  qc_status          VARCHAR(32),
  qc_plan_id         VARCHAR(32),
  smart_bid_type     VARCHAR(32),
  adlab_scene        VARCHAR(32),
  budget             DECIMAL(14,2),
  roi2_goal          DECIMAL(10,2),
  start_date         DATE,
  end_date           DATE,
  qcpx_mode          VARCHAR(32),
  audience_json      JSON,
  publish_version    INT NOT NULL DEFAULT 0,
  idempotency_key    VARCHAR(64),
  reject_reason      TEXT,
  source             VARCHAR(16) NOT NULL DEFAULT 'LINGTOU',
  created_at         DATETIME(3) NOT NULL DEFAULT (CURRENT_TIMESTAMP(3)),
  name_active        VARCHAR(30) GENERATED ALWAYS AS (
    CASE
      WHEN status COLLATE utf8mb4_bin <> 'finished' THEN name
      ELSE NULL
    END
  ) STORED,
  UNIQUE KEY uk_ad_plan_aweme_name (tenant_id, aweme_id, name_active)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS creative_task (
  id           VARCHAR(36) PRIMARY KEY,
  tenant_id    VARCHAR(36) NOT NULL,
  plan_id      VARCHAR(36),
  status       VARCHAR(16) NOT NULL,
  mode         VARCHAR(16) NOT NULL DEFAULT 'STANDARD',
  error_msg    TEXT,
  created_at   DATETIME(3) NOT NULL DEFAULT (CURRENT_TIMESTAMP(3)),
  finished_at  DATETIME(3)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS creative_package (
  id                  VARCHAR(36) PRIMARY KEY,
  tenant_id           VARCHAR(36) NOT NULL,
  task_id             VARCHAR(36) NOT NULL,
  version             INT NOT NULL DEFAULT 1,
  compliance_status   VARCHAR(16),
  artifacts_json      JSON NOT NULL,
  confirmed_at        DATETIME(3)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ad_plan_creative (
  ad_plan_id      VARCHAR(36) NOT NULL,
  package_id      VARCHAR(36) NOT NULL,
  product_id      VARCHAR(32) NOT NULL,
  video_id        VARCHAR(32),
  cover_image_id  VARCHAR(32),
  titles_json     JSON,
  PRIMARY KEY (ad_plan_id, product_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS plan_metric_snapshot (
  id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id     VARCHAR(36) NOT NULL,
  ad_plan_id    VARCHAR(36),
  qc_plan_id    VARCHAR(32),
  snapshot_ts   DATETIME(3) NOT NULL,
  impression    BIGINT,
  click         BIGINT,
  cost          DECIMAL(14,4),
  `convert`      BIGINT,
  gmv           DECIMAL(14,4),
  roi           DECIMAL(10,4),
  ctr           DECIMAL(10,6),
  INDEX idx_snapshot_plan_ts (ad_plan_id, snapshot_ts DESC)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS agent_action (
  id                VARCHAR(36) PRIMARY KEY,
  tenant_id         VARCHAR(36) NOT NULL,
  ad_plan_id        VARCHAR(36) NOT NULL,
  action_type       VARCHAR(32) NOT NULL,
  risk_level        VARCHAR(8),
  before_json       JSON,
  after_json        JSON,
  snapshot_ts       DATETIME(3) NOT NULL,
  result            VARCHAR(16),
  idempotency_key   VARCHAR(64),
  UNIQUE (ad_plan_id, action_type, snapshot_ts)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS notification (
  id          VARCHAR(36) PRIMARY KEY,
  tenant_id   VARCHAR(36) NOT NULL,
  channel     VARCHAR(16) NOT NULL DEFAULT 'INBOX',
  title       VARCHAR(128) NOT NULL,
  body        TEXT,
  biz_type    VARCHAR(32),
  biz_id      VARCHAR(64),
  read_at     DATETIME(3),
  sent_at     DATETIME(3) NOT NULL DEFAULT (CURRENT_TIMESTAMP(3))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS billing_trial (
  tenant_id    VARCHAR(36) PRIMARY KEY,
  started_at   DATETIME(3) NOT NULL,
  expires_at   DATETIME(3) NOT NULL
) ENGINE=InnoDB;
