ALTER TABLE sys_user ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE platform_phone_access (
    phone VARCHAR(20) PRIMARY KEY,
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(300),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_platform_phone_blocked ON platform_phone_access(blocked, updated_at, phone);
