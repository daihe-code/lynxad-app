-- Engine outputs already live in the engine's OSS. Keep provenance, not a second file.
CREATE TABLE IF NOT EXISTS creative_engine_asset (
    asset_id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    job_id VARCHAR(128) NOT NULL,
    material_id VARCHAR(128) NOT NULL,
    component VARCHAR(32) NOT NULL,
    source_url TEXT,
    UNIQUE KEY uq_engine_component (tenant_id, job_id, material_id, component)
);
