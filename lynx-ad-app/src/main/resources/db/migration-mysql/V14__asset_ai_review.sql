CREATE TABLE creative_asset_review (
    asset_id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    review_id VARCHAR(128),
    decision VARCHAR(32),
    summary TEXT,
    reasons_json TEXT,
    error_message VARCHAR(500),
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(3) NOT NULL,
    lease_until TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    INDEX idx_asset_review_due (status,next_attempt_at)
);
INSERT INTO creative_asset_review(asset_id,tenant_id,next_attempt_at,created_at,updated_at)
SELECT id,tenant_id,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)
FROM creative_asset WHERE storage_status='STORED';
