ALTER TABLE creative_asset
    ALTER COLUMN object_key DROP NOT NULL,
    ADD COLUMN bucket VARCHAR(128),
    ADD COLUMN storage_endpoint VARCHAR(255),
    ADD COLUMN path_version VARCHAR(16) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN origin VARCHAR(32) NOT NULL DEFAULT 'LOCAL_UPLOAD',
    ADD COLUMN duration_ms BIGINT,
    ADD COLUMN etag VARCHAR(128),
    ADD COLUMN preview_asset_id VARCHAR(36);
UPDATE creative_asset SET origin='AI_GENERATED' WHERE EXISTS
    (SELECT 1 FROM creative_engine_asset e WHERE e.asset_id=creative_asset.id AND e.tenant_id=creative_asset.tenant_id);
ALTER TABLE creative_asset_platform ADD COLUMN platform VARCHAR(32) NOT NULL DEFAULT 'QIANCHUAN';
ALTER TABLE creative_carousel_platform ADD COLUMN platform VARCHAR(32) NOT NULL DEFAULT 'QIANCHUAN';
