ALTER TABLE creative_asset_platform ADD COLUMN attempted_signature VARCHAR(32);
ALTER TABLE creative_asset_platform ADD COLUMN upload_evidence_version INT NOT NULL DEFAULT 0;
ALTER TABLE creative_asset_platform ADD COLUMN attempted_advertiser_id VARCHAR(64);
ALTER TABLE creative_asset_platform ADD COLUMN upload_dispatched_at TIMESTAMP(3) NULL;
ALTER TABLE creative_asset_platform ADD COLUMN verification_key VARCHAR(36);
ALTER TABLE creative_asset_platform ADD COLUMN verification_lease_until TIMESTAMP(3) NULL;
ALTER TABLE creative_asset_platform ADD COLUMN verification_checked_at TIMESTAMP(3) NULL;
ALTER TABLE creative_asset_platform ADD COLUMN verification_state VARCHAR(24);
ALTER TABLE creative_asset_platform ADD COLUMN verification_message VARCHAR(512);
-- Old catch-all/parse failures could follow an accepted upload. Legacy 1511 also conflated
-- explicit rejection with a response missing its business code; without evidence, do not replay.
UPDATE creative_asset_platform SET status='UNCERTAIN',error_message='历史同步结果缺少确定回执，请先核查，不可直接重传'
 WHERE status='FAILED' AND attempts>0 AND error_message IN ('文件读取或同步任务失败，请重试','千川拒绝上传或文件不符合要求，请检查格式及素材上传权限后重试（错误码 9999）','千川拒绝上传或文件不符合要求，请检查格式及素材上传权限后重试（错误码 1511）');
