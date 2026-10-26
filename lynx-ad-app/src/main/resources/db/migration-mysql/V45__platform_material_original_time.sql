-- Unknown provider creation times must not be replaced with the catalog refresh time.
ALTER TABLE platform_material_catalog MODIFY COLUMN created_at TIMESTAMP(3) NULL DEFAULT NULL;
