ALTER TABLE creative_task
  ADD COLUMN product_ids_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN steps_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN package_id VARCHAR(36);
