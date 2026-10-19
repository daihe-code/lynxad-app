-- A one-way fingerprint prevents polling from retrying the same rejected token.
SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE creative_asset_platform ADD COLUMN credential_fingerprint VARCHAR(64) NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'creative_asset_platform' AND column_name = 'credential_fingerprint');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;
