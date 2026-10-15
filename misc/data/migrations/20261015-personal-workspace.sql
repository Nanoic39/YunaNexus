-- 在认证库及文件物理库 0 执行；仅新增表，不修改既有数据。
USE yunanexus_auth;
CREATE TABLE IF NOT EXISTS auth_personal_api_key (
 id VARCHAR(32) PRIMARY KEY, user_id BIGINT NOT NULL, name VARCHAR(64) NOT NULL,
 token_prefix VARCHAR(16) NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE,
 scopes VARCHAR(256) NOT NULL, created_at DATETIME NOT NULL, expires_at DATETIME NOT NULL,
 last_used_at DATETIME NULL, revoked TINYINT NOT NULL DEFAULT 0,
 INDEX idx_owner(user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
USE yunanexus_file_0;
CREATE TABLE IF NOT EXISTS file_share (
 share_id CHAR(43) PRIMARY KEY, user_id BIGINT NOT NULL, file_id BIGINT NOT NULL,
 file_name VARCHAR(255) NOT NULL, code_hash CHAR(64) NULL,
 created_at DATETIME NOT NULL, expires_at DATETIME NOT NULL, revoked TINYINT NOT NULL DEFAULT 0,
 failed_attempts INT NOT NULL DEFAULT 0, failure_window DATETIME NULL,
 INDEX idx_owner(user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
