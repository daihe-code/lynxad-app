CREATE TABLE user_welcome_ack (
  user_id VARCHAR(36) PRIMARY KEY,
  acknowledged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
INSERT INTO user_welcome_ack(user_id) SELECT id FROM sys_user;
