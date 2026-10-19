CREATE TABLE IF NOT EXISTS oauth_app_binding (
  id                VARCHAR(36) PRIMARY KEY,
  tenant_id         VARCHAR(36) NOT NULL,
  advertiser_id     VARCHAR(32) NOT NULL,
  app_code          VARCHAR(32) NOT NULL,
  oauth_account_id  VARCHAR(36) NOT NULL REFERENCES oauth_account(id),
  created_at        DATETIME(3) NOT NULL,
  UNIQUE (tenant_id, advertiser_id, app_code)
) ENGINE=InnoDB;
