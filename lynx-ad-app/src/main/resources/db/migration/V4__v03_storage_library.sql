ALTER TABLE creative_package
  ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE ad_plan_creative
  ALTER COLUMN video_id TYPE VARCHAR(64),
  ALTER COLUMN cover_image_id TYPE VARCHAR(64);

ALTER TABLE ad_plan_creative
  ADD COLUMN material_id VARCHAR(64);

CREATE INDEX idx_ad_plan_creative_video ON ad_plan_creative (video_id);
CREATE INDEX idx_ad_plan_creative_material ON ad_plan_creative (material_id);
