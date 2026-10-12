-- Retain deleted plan records for shared media/history without reserving their names.
ALTER TABLE ad_plan MODIFY COLUMN name_active VARCHAR(128) GENERATED ALWAYS AS (
  CASE WHEN UPPER(status) NOT IN ('FINISHED', 'DELETED') THEN name ELSE NULL END
) STORED;
