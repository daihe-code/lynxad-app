ALTER TABLE qc_plan_metadata ADD COLUMN last_report LONGTEXT;
ALTER TABLE qc_plan_metadata ADD COLUMN last_report_date DATE;
ALTER TABLE qc_plan_metadata ADD COLUMN last_report_ts DATETIME(3);

-- Preserve existing snapshots before the next daily synchronization overwrites the rolling columns.
UPDATE qc_plan_metadata SET
  last_report=CASE WHEN today_report IS NOT NULL AND today_report NOT IN ('null','{}') THEN today_report ELSE yesterday_report END,
  last_report_date=CASE WHEN today_report IS NOT NULL AND today_report NOT IN ('null','{}') THEN report_date ELSE DATE_SUB(report_date, INTERVAL 1 DAY) END,
  last_report_ts=report_ts
WHERE report_date IS NOT NULL AND (today_report IS NOT NULL OR yesterday_report IS NOT NULL);
