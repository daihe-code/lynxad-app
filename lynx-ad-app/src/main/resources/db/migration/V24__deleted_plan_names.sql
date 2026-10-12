-- Retain deleted plan records for shared media/history without reserving their names.
DROP INDEX IF EXISTS uk_ad_plan_aweme_name;
CREATE UNIQUE INDEX uk_ad_plan_aweme_name ON ad_plan(tenant_id, aweme_id, name)
  WHERE UPPER(status) NOT IN ('FINISHED', 'DELETED');
