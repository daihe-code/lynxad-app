ALTER TABLE creative_asset ADD COLUMN library_type VARCHAR(16);
CREATE TABLE creative_carousel (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    accounts_json TEXT NOT NULL,
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    next_check_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    UNIQUE KEY uq_carousel_request (tenant_id,request_id),
    INDEX ix_carousel_review (review_status,next_check_at)
);
CREATE TABLE creative_carousel_part (
    carousel_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    position INT NOT NULL,
    asset_id VARCHAR(36) NOT NULL,
    PRIMARY KEY(carousel_id,role,position),
    INDEX ix_carousel_part_asset (tenant_id,asset_id),
    FOREIGN KEY(carousel_id) REFERENCES creative_carousel(id),
    FOREIGN KEY(asset_id) REFERENCES creative_asset(id)
);
