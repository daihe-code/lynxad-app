-- V19 used an unnamed unique constraint. Rebuild its small provenance table
-- with the same rows and primary key, allowing multiple immutable file versions.
CREATE TABLE creative_engine_asset_versioned (
    asset_id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    job_id VARCHAR(128) NOT NULL,
    material_id VARCHAR(128) NOT NULL,
    component VARCHAR(32) NOT NULL,
    source_url TEXT
);
INSERT INTO creative_engine_asset_versioned SELECT asset_id,tenant_id,job_id,material_id,component,source_url FROM creative_engine_asset;
DROP TABLE creative_engine_asset;
ALTER TABLE creative_engine_asset_versioned RENAME TO creative_engine_asset;
CREATE INDEX idx_engine_component ON creative_engine_asset (tenant_id, job_id, material_id, component);
