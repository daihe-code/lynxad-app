CREATE TABLE user_welcome_ack (
  user_id VARCHAR(36) PRIMARY KEY REFERENCES sys_user(id),
  acknowledged_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Existing users have already entered the product; only new registrations need the welcome step.
INSERT INTO user_welcome_ack(user_id) SELECT id FROM sys_user;
