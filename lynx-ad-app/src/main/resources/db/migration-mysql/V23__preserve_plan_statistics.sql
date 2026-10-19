SET @lynx_backfill = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'qc_plan_metadata' AND column_name = 'last_report');

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE qc_plan_metadata ADD COLUMN last_report LONGTEXT', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'qc_plan_metadata' AND column_name = 'last_report');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE qc_plan_metadata ADD COLUMN last_report_date DATE', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'qc_plan_metadata' AND column_name = 'last_report_date');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE qc_plan_metadata ADD COLUMN last_report_ts DATETIME(3)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'qc_plan_metadata' AND column_name = 'last_report_ts');
PREPARE lynx_stmt FROM @ddl;
EXECUTE lynx_stmt;
DEALLOCATE PREPARE lynx_stmt;

-- Preserve existing snapshots before the next daily synchronization overwrites the rolling columns.
UPDATE qc_plan_metadata SET
  last_report=CASE WHEN today_report IS NOT NULL AND today_report NOT IN ('null','{}') THEN today_report ELSE yesterday_report END,
  last_report_date=CASE WHEN today_report IS NOT NULL AND today_report NOT IN ('null','{}') THEN report_date ELSE DATE_SUB(report_date, INTERVAL 1 DAY) END,
  last_report_ts=report_ts
WHERE @lynx_backfill AND report_date IS NOT NULL AND (today_report IS NOT NULL OR yesterday_report IS NOT NULL);
