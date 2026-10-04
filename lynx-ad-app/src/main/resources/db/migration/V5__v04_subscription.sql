CREATE TABLE billing_plan (
  code              VARCHAR(32) PRIMARY KEY,
  name              VARCHAR(32) NOT NULL,
  list_price_fen    INTEGER NOT NULL,
  ad_account_limit  INTEGER NOT NULL,
  sort_no           INTEGER NOT NULL,
  benefits_json     JSONB NOT NULL,
  suitable_for      VARCHAR(128) NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE billing_order (
  id                VARCHAR(36) PRIMARY KEY,
  tenant_id         VARCHAR(36) NOT NULL,
  plan_code         VARCHAR(32) NOT NULL,
  from_plan_code    VARCHAR(32),
  type              VARCHAR(24) NOT NULL,
  status            VARCHAR(24) NOT NULL,
  list_price_fen    INTEGER NOT NULL,
  payable_fen       INTEGER NOT NULL,
  discount_label    VARCHAR(64),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  paid_at           TIMESTAMPTZ
);
CREATE INDEX idx_billing_order_tenant ON billing_order (tenant_id, created_at DESC);

CREATE TABLE billing_payment (
  id                VARCHAR(36) PRIMARY KEY,
  tenant_id         VARCHAR(36) NOT NULL,
  order_id          VARCHAR(36) NOT NULL,
  channel           VARCHAR(16) NOT NULL,
  status            VARCHAR(24) NOT NULL,
  amount_fen        INTEGER NOT NULL,
  channel_trade_no  VARCHAR(64),
  mock_nonce        VARCHAR(64),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at      TIMESTAMPTZ
);
CREATE INDEX idx_billing_payment_order ON billing_payment (order_id);

CREATE TABLE billing_subscription (
  tenant_id            VARCHAR(36) PRIMARY KEY,
  plan_code            VARCHAR(32) NOT NULL,
  status               VARCHAR(24) NOT NULL,
  period_start         TIMESTAMPTZ NOT NULL,
  period_end           TIMESTAMPTZ NOT NULL,
  auto_renew           BOOLEAN NOT NULL DEFAULT TRUE,
  pay_method           VARCHAR(16) NOT NULL,
  paid_fen_this_period INTEGER NOT NULL,
  next_plan_code       VARCHAR(32),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO billing_plan (code, name, list_price_fen, ad_account_limit, sort_no, benefits_json, suitable_for) VALUES
('BASIC', '基础版', 29900, 1, 1, '["1 个广告账户","基础智能投放","效果数据分析","社区支持"]', '适合小微 / 本地商家'),
('PROFESSIONAL', '专业版', 79900, 1, 2, '["1 个广告账户","全部智能投放","自动优化策略","投放效果深度分析","素材 A/B 测试","专属客服支持"]', '适合成长型电商 / 本地生活'),
('FLAGSHIP', '旗舰版', 299900, 3, 3, '["3 个广告账户","全部专业权益","多账户统一管理","优先排期定制","专属资深经理","API 高级接口"]', '适合多店 / 连锁商家');
