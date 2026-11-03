-- Generated files now receive persistent names containing their full asset ID.
-- Repair legacy image placeholders without changing object paths or platform receipts.
UPDATE creative_asset
SET filename = CONCAT('智能图片_', id,
    CASE LOWER(filename)
        WHEN 'generated.png' THEN '.png'
        WHEN 'generated.jpeg' THEN '.jpeg'
        ELSE '.jpg'
    END)
WHERE kind = 'IMAGE'
  AND storage_mode = 'engine_oss'
  AND LOWER(filename) IN ('generated.jpg', 'generated.jpeg', 'generated.png');
