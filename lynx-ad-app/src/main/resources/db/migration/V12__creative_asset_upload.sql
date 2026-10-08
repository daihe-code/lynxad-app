CREATE TABLE creative_asset (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, user_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL, kind VARCHAR(16) NOT NULL, filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL, size_bytes BIGINT NOT NULL, object_key VARCHAR(512) NOT NULL,
    storage_status VARCHAR(24) NOT NULL, storage_mode VARCHAR(16) NOT NULL,
    upload_id VARCHAR(256), upload_ticket_json TEXT, content_key VARCHAR(64), md5 VARCHAR(32),
    is_aigc BOOLEAN NOT NULL DEFAULT FALSE, width INT, height INT, text_content TEXT,
    canonical_id VARCHAR(36), error_message VARCHAR(512), created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    UNIQUE (tenant_id, request_id), UNIQUE (tenant_id, content_key)
);
CREATE INDEX ix_asset_tenant ON creative_asset(tenant_id, created_at);
CREATE TABLE creative_asset_platform (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, asset_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(64) NOT NULL, advertiser_id VARCHAR(64), status VARCHAR(24) NOT NULL,
    object_id VARCHAR(128), material_id VARCHAR(128), request_id VARCHAR(128),
    review_status VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN', attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL, lease_until TIMESTAMP, error_message VARCHAR(512),
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, UNIQUE (tenant_id, asset_id, account_id)
);
CREATE INDEX ix_asset_sync_queue ON creative_asset_platform(status, next_attempt_at);
CREATE INDEX ix_asset_sync_tenant ON creative_asset_platform(tenant_id, asset_id);
