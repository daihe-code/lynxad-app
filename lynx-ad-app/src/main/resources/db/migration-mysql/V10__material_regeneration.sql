SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE creative_task ADD COLUMN regeneration_package_id VARCHAR(36) NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_task' AND column_name = 'regeneration_package_id');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE creative_task ADD COLUMN regeneration_type VARCHAR(16) NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_task' AND column_name = 'regeneration_type');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE creative_task ADD COLUMN regeneration_version INT NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_task' AND column_name = 'regeneration_version');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;
