-- Unknown provider creation times must not be replaced with the catalog refresh time.
ALTER TABLE platform_material_catalog ALTER COLUMN created_at DROP NOT NULL;
