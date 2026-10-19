SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE creative_package ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT (CURRENT_TIMESTAMP(3))', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_package' AND column_name = 'created_at');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

ALTER TABLE ad_plan_creative
  MODIFY COLUMN video_id VARCHAR(64) NULL,
  MODIFY COLUMN cover_image_id VARCHAR(64) NULL;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE ad_plan_creative ADD COLUMN material_id VARCHAR(64)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ad_plan_creative' AND column_name = 'material_id');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'CREATE INDEX idx_ad_plan_creative_video ON ad_plan_creative (video_id)', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ad_plan_creative' AND index_name = 'idx_ad_plan_creative_video');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'CREATE INDEX idx_ad_plan_creative_material ON ad_plan_creative (material_id)', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ad_plan_creative' AND index_name = 'idx_ad_plan_creative_material');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;
