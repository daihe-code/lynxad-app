-- Track the current review cycle, not the age of the draft or plan.
-- Existing pending plans start timing on their first successful local observation.
ALTER TABLE ad_plan ADD COLUMN audit_pending_since TIMESTAMP NULL;
ALTER TABLE ad_plan ADD COLUMN audit_last_checked_at TIMESTAMP NULL;
ALTER TABLE ad_plan ADD COLUMN audit_last_result VARCHAR(24) NULL;
ALTER TABLE ad_plan ADD COLUMN audit_next_check_at TIMESTAMP NULL;
ALTER TABLE ad_plan ADD COLUMN audit_check_key VARCHAR(64) NULL;
ALTER TABLE ad_plan ADD COLUMN audit_lease_until TIMESTAMP NULL;
ALTER TABLE ad_plan ADD COLUMN audit_last_attempt_at TIMESTAMP NULL;
