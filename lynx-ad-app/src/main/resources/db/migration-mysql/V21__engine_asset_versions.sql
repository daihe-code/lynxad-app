-- Keep immutable file versions when the engine updates a material component.
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE creative_engine_asset DROP INDEX uq_engine_component', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'creative_engine_asset' AND index_name = 'uq_engine_component');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'CREATE INDEX idx_engine_component ON creative_engine_asset (tenant_id, job_id, material_id, component)', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'creative_engine_asset' AND index_name = 'idx_engine_component');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;
