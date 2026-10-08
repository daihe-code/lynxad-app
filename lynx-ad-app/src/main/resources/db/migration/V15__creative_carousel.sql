ALTER TABLE creative_asset ADD COLUMN library_type VARCHAR(16);
CREATE TABLE creative_carousel (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, user_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL, name VARCHAR(100) NOT NULL, accounts_json TEXT NOT NULL,
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING', next_check_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL, UNIQUE(tenant_id,request_id)
);
CREATE INDEX ix_carousel_review ON creative_carousel(review_status,next_check_at);
CREATE TABLE creative_carousel_part (
    carousel_id VARCHAR(36) NOT NULL REFERENCES creative_carousel(id), tenant_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL, position INT NOT NULL, asset_id VARCHAR(36) NOT NULL REFERENCES creative_asset(id),
    PRIMARY KEY(carousel_id,role,position)
);
CREATE INDEX ix_carousel_part_asset ON creative_carousel_part(tenant_id,asset_id);
