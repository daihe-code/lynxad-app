ALTER TABLE creative_package
  ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT (CURRENT_TIMESTAMP(3));

ALTER TABLE ad_plan_creative
  MODIFY COLUMN video_id VARCHAR(64) NULL,
  MODIFY COLUMN cover_image_id VARCHAR(64) NULL;

ALTER TABLE ad_plan_creative
  ADD COLUMN material_id VARCHAR(64);

CREATE INDEX idx_ad_plan_creative_video ON ad_plan_creative (video_id);
CREATE INDEX idx_ad_plan_creative_material ON ad_plan_creative (material_id);
