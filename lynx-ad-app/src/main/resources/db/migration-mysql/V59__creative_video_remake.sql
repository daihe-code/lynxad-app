CREATE TABLE creative_video_remake (
    tenant_id VARCHAR(128) NOT NULL,
    id VARCHAR(36) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    binding_id VARCHAR(128) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    engine_id VARCHAR(64),
    phase VARCHAR(24) NOT NULL,
    request_json TEXT NOT NULL,
    confirmation_json TEXT,
    asset_id VARCHAR(36),
    error_message VARCHAR(240),
    lease_token VARCHAR(36),
    next_check_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, id)
);
CREATE INDEX idx_video_remake_work ON creative_video_remake (phase, next_check_at);
CREATE INDEX idx_video_remake_history ON creative_video_remake (tenant_id, actor_id, created_at);

CREATE TABLE creative_video_remake_upload (
    tenant_id VARCHAR(128) NOT NULL,
    upload_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    binding_id VARCHAR(128) NOT NULL,
    media_type VARCHAR(8) NOT NULL,
    asset_id VARCHAR(64),
    PRIMARY KEY (tenant_id, upload_id)
);
CREATE INDEX idx_video_remake_upload_asset ON creative_video_remake_upload (tenant_id, asset_id);
