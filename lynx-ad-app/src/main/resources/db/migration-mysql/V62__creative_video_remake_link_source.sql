-- 链接导入的来源记录：原始链接、来源平台与下载方式。
ALTER TABLE creative_video_remake_upload
  ADD COLUMN source_url VARCHAR(1024),
  ADD COLUMN source_platform VARCHAR(32),
  ADD COLUMN download_method VARCHAR(32);
