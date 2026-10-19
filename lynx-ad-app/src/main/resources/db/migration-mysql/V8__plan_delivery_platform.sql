SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE ad_plan ADD COLUMN delivery_platform VARCHAR(16) NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ad_plan' AND column_name = 'delivery_platform');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;
