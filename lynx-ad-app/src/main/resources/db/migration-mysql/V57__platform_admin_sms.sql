ALTER TABLE platform_admin_account
  ADD COLUMN phone VARCHAR(11) UNIQUE,
  ADD COLUMN can_manage_admins BOOLEAN NOT NULL DEFAULT FALSE,
  MODIFY COLUMN password_hash VARCHAR(128) NULL;

UPDATE platform_admin_session SET revoked_at = CURRENT_TIMESTAMP WHERE revoked_at IS NULL;

INSERT INTO platform_admin_account
  (id, username, display_name, status, phone, can_manage_admins, created_at, updated_at)
VALUES
  ('6cbeebc1-75d7-4991-8197-1806502559a2', '+86 13810900372', '平台管理员', 'ACTIVE', '13810900372', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('e2346152-ed01-44c1-ae16-889da66ee783', '+86 18801061264', '平台管理员', 'ACTIVE', '18801061264', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
