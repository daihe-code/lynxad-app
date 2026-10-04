-- LynxAd Flyway V1. Table names frozen.

CREATE TABLE sys_tenant (
  id            VARCHAR(36) PRIMARY KEY,
  name          VARCHAR(128) NOT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE sys_user (
  id             VARCHAR(36) PRIMARY KEY,
  phone          VARCHAR(20) NOT NULL UNIQUE,
  password_hash  VARCHAR(128),
  status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tenant_member (
  tenant_id    VARCHAR(36) NOT NULL REFERENCES sys_tenant(id),
  user_id      VARCHAR(36) NOT NULL REFERENCES sys_user(id),
  member_role  VARCHAR(16) NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, user_id)
);

CREATE TABLE user_profile (
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
  completed_at     TIMESTAMPTZ
);

CREATE TABLE oauth_account (
  id                   VARCHAR(36) PRIMARY KEY,
  tenant_id            VARCHAR(36) NOT NULL,
  platform             VARCHAR(16) NOT NULL,
  advertiser_id        VARCHAR(32) NOT NULL,
  account_name         VARCHAR(128),
  access_token_enc     TEXT NOT NULL,
  refresh_token_enc    TEXT,
  token_expires_at     TIMESTAMPTZ,
  refresh_expires_at   TIMESTAMPTZ,
  status               VARCHAR(16) NOT NULL,
  authorized_at        TIMESTAMPTZ,
  UNIQUE (tenant_id, platform, advertiser_id)
);

CREATE TABLE oauth_aweme (
  id                VARCHAR(36) PRIMARY KEY,
  oauth_account_id  VARCHAR(36) NOT NULL REFERENCES oauth_account(id),
  aweme_id          VARCHAR(32) NOT NULL,
  name              VARCHAR(128),
  avatar            VARCHAR(512),
  aweme_type        VARCHAR(24) NOT NULL,
  is_primary        BOOLEAN NOT NULL DEFAULT false,
  UNIQUE (oauth_account_id, aweme_id)
);

CREATE TABLE product (
  id               VARCHAR(36) PRIMARY KEY,
  tenant_id        VARCHAR(36) NOT NULL,
  aweme_id         VARCHAR(32) NOT NULL,
  qc_product_id    VARCHAR(32) NOT NULL,
  name             VARCHAR(256),
  price            NUMERIC(12,2),
  user_price       NUMERIC(12,2),
  image_url        VARCHAR(512),
  tags_json        JSONB,
  analysis_json    JSONB,
  potential_score  NUMERIC(6,2),
  gray_reason      VARCHAR(256),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, aweme_id, qc_product_id)
);

CREATE TABLE ad_plan (
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
  budget             NUMERIC(14,2),
  roi2_goal          NUMERIC(10,2),
  start_date         DATE,
  end_date           DATE,
  qcpx_mode          VARCHAR(32),
  audience_json      JSONB,
  publish_version    INT NOT NULL DEFAULT 0,
  idempotency_key    VARCHAR(64),
  reject_reason      TEXT,
  source             VARCHAR(16) NOT NULL DEFAULT 'LINGTOU',
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_ad_plan_aweme_name ON ad_plan(tenant_id, aweme_id, name)
  WHERE status <> 'finished';

CREATE TABLE creative_task (
  id           VARCHAR(36) PRIMARY KEY,
  tenant_id    VARCHAR(36) NOT NULL,
  plan_id      VARCHAR(36),
  status       VARCHAR(16) NOT NULL,
  mode         VARCHAR(16) NOT NULL DEFAULT 'STANDARD',
  error_msg    TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at  TIMESTAMPTZ
);

CREATE TABLE creative_package (
  id                  VARCHAR(36) PRIMARY KEY,
  tenant_id           VARCHAR(36) NOT NULL,
  task_id             VARCHAR(36) NOT NULL,
  version             INT NOT NULL DEFAULT 1,
  compliance_status   VARCHAR(16),
  artifacts_json      JSONB NOT NULL,
  confirmed_at        TIMESTAMPTZ
);

CREATE TABLE ad_plan_creative (
  ad_plan_id      VARCHAR(36) NOT NULL,
  package_id      VARCHAR(36) NOT NULL,
  product_id      VARCHAR(32) NOT NULL,
  video_id        VARCHAR(32),
  cover_image_id  VARCHAR(32),
  titles_json     JSONB,
  PRIMARY KEY (ad_plan_id, product_id)
);

CREATE TABLE plan_metric_snapshot (
  id            BIGSERIAL PRIMARY KEY,
  tenant_id     VARCHAR(36) NOT NULL,
  ad_plan_id    VARCHAR(36),
  qc_plan_id    VARCHAR(32),
  snapshot_ts   TIMESTAMPTZ NOT NULL,
  impression    BIGINT,
  click         BIGINT,
  cost          NUMERIC(14,4),
  convert       BIGINT,
  gmv           NUMERIC(14,4),
  roi           NUMERIC(10,4),
  ctr           NUMERIC(10,6)
);
CREATE INDEX idx_snapshot_plan_ts ON plan_metric_snapshot(ad_plan_id, snapshot_ts DESC);

CREATE TABLE agent_action (
  id                VARCHAR(36) PRIMARY KEY,
  tenant_id         VARCHAR(36) NOT NULL,
  ad_plan_id        VARCHAR(36) NOT NULL,
  action_type       VARCHAR(32) NOT NULL,
  risk_level        VARCHAR(8),
  before_json       JSONB,
  after_json        JSONB,
  snapshot_ts       TIMESTAMPTZ NOT NULL,
  result            VARCHAR(16),
  idempotency_key   VARCHAR(64),
  UNIQUE (ad_plan_id, action_type, snapshot_ts)
);

CREATE TABLE notification (
  id          VARCHAR(36) PRIMARY KEY,
  tenant_id   VARCHAR(36) NOT NULL,
  channel     VARCHAR(16) NOT NULL DEFAULT 'INBOX',
  title       VARCHAR(128) NOT NULL,
  body        TEXT,
  biz_type    VARCHAR(32),
  biz_id      VARCHAR(64),
  read_at     TIMESTAMPTZ,
  sent_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE billing_trial (
  tenant_id    VARCHAR(36) PRIMARY KEY,
  started_at   TIMESTAMPTZ NOT NULL,
  expires_at   TIMESTAMPTZ NOT NULL
);
