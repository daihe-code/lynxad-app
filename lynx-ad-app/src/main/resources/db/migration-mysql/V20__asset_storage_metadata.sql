ALTER TABLE creative_asset
    MODIFY COLUMN object_key VARCHAR(512) NULL,
    ADD COLUMN bucket VARCHAR(128) NULL,
    ADD COLUMN storage_endpoint VARCHAR(255) NULL,
    ADD COLUMN path_version VARCHAR(16) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN origin VARCHAR(32) NOT NULL DEFAULT 'LOCAL_UPLOAD',
    ADD COLUMN duration_ms BIGINT NULL,
    ADD COLUMN etag VARCHAR(128) NULL,
    ADD COLUMN preview_asset_id VARCHAR(36) NULL;
UPDATE creative_asset SET origin='AI_GENERATED' WHERE EXISTS
    (SELECT 1 FROM creative_engine_asset e WHERE e.asset_id=creative_asset.id AND e.tenant_id=creative_asset.tenant_id);
ALTER TABLE creative_asset_platform ADD COLUMN platform VARCHAR(32) NOT NULL DEFAULT 'QIANCHUAN';
ALTER TABLE creative_carousel_platform ADD COLUMN platform VARCHAR(32) NOT NULL DEFAULT 'QIANCHUAN';
