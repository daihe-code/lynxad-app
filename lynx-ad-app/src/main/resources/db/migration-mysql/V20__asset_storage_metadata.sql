ALTER TABLE creative_asset
    MODIFY COLUMN object_key VARCHAR(512) NULL;

SET @lynx_fill_origin = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_asset' AND column_name = 'origin');

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE creative_asset ADD COLUMN bucket VARCHAR(128) NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_asset' AND column_name = 'bucket');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE creative_asset ADD COLUMN storage_endpoint VARCHAR(255) NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_asset' AND column_name = 'storage_endpoint');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE creative_asset ADD COLUMN path_version VARCHAR(16) NOT NULL DEFAULT ''LEGACY''', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_asset' AND column_name = 'path_version');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE creative_asset ADD COLUMN origin VARCHAR(32) NOT NULL DEFAULT ''LOCAL_UPLOAD''', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_asset' AND column_name = 'origin');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE creative_asset ADD COLUMN duration_ms BIGINT NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_asset' AND column_name = 'duration_ms');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE creative_asset ADD COLUMN etag VARCHAR(128) NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_asset' AND column_name = 'etag');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE creative_asset ADD COLUMN preview_asset_id VARCHAR(36) NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_asset' AND column_name = 'preview_asset_id');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

UPDATE creative_asset SET origin='AI_GENERATED' WHERE @lynx_fill_origin AND EXISTS
    (SELECT 1 FROM creative_engine_asset e WHERE e.asset_id=creative_asset.id AND e.tenant_id=creative_asset.tenant_id);

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE creative_asset_platform ADD COLUMN platform VARCHAR(32) NOT NULL DEFAULT ''QIANCHUAN''', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_asset_platform' AND column_name = 'platform');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE creative_carousel_platform ADD COLUMN platform VARCHAR(32) NOT NULL DEFAULT ''QIANCHUAN''', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_carousel_platform' AND column_name = 'platform');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;
