-- Keep immutable file versions when the engine updates a material component.
ALTER TABLE creative_engine_asset DROP INDEX uq_engine_component;
CREATE INDEX idx_engine_component ON creative_engine_asset (tenant_id, job_id, material_id, component);
