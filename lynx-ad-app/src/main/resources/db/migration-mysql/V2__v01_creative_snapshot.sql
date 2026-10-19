SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'creative_task'
        AND column_name = 'product_ids_json'
    ),
    'SELECT 1',
    'ALTER TABLE creative_task ADD COLUMN product_ids_json JSON NOT NULL DEFAULT (JSON_ARRAY())'
  )
);
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'creative_task'
        AND column_name = 'steps_json'
    ),
    'SELECT 1',
    'ALTER TABLE creative_task ADD COLUMN steps_json JSON NOT NULL DEFAULT (JSON_ARRAY())'
  )
);
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'creative_task'
        AND column_name = 'package_id'
    ),
    'SELECT 1',
    'ALTER TABLE creative_task ADD COLUMN package_id VARCHAR(36)'
  )
);
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;
