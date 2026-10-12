ALTER TABLE qc_plan_metadata ADD COLUMN last_report TEXT;
ALTER TABLE qc_plan_metadata ADD COLUMN last_report_date DATE;
ALTER TABLE qc_plan_metadata ADD COLUMN last_report_ts TIMESTAMPTZ;

UPDATE qc_plan_metadata SET
  last_report=CASE WHEN today_report IS NOT NULL AND today_report NOT IN ('null','{}') THEN today_report ELSE yesterday_report END,
  last_report_date=CASE WHEN today_report IS NOT NULL AND today_report NOT IN ('null','{}') THEN report_date ELSE report_date - 1 END,
  last_report_ts=report_ts
WHERE report_date IS NOT NULL AND (today_report IS NOT NULL OR yesterday_report IS NOT NULL);
