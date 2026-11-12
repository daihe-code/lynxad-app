UPDATE billing_trial t
SET expires_at = TIMESTAMPADD(HOUR, 72, t.started_at)
WHERE t.expires_at > CURRENT_TIMESTAMP
  AND t.expires_at = TIMESTAMPADD(HOUR, 168, t.started_at)
  AND NOT EXISTS (
    SELECT 1 FROM platform_package_adjustment a
    WHERE a.tenant_id = t.tenant_id AND a.grant_type = 'TRIAL' AND a.after_end = t.expires_at
  );
