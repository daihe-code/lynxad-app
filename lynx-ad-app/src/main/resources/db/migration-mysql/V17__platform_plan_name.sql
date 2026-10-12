-- Keep platform names intact; local creation still enforces its 30-character rule.
ALTER TABLE ad_plan MODIFY COLUMN name VARCHAR(128) NOT NULL;
ALTER TABLE ad_plan MODIFY COLUMN name_active VARCHAR(128) GENERATED ALWAYS AS (
  CASE WHEN status COLLATE utf8mb4_bin <> 'finished' THEN name ELSE NULL END
) STORED;
