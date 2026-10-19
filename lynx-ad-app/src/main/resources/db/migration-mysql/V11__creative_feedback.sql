SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE creative_task ADD COLUMN feedback_json JSON NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_task' AND column_name = 'feedback_json');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;
