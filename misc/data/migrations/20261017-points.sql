USE yunanexus_auth;
CREATE TABLE IF NOT EXISTS auth_points_account (
 user_id BIGINT NOT NULL PRIMARY KEY,balance BIGINT NOT NULL DEFAULT 0,streak INT NOT NULL DEFAULT 0,
 last_check_in DATE NULL,CONSTRAINT chk_points_balance CHECK(balance>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS auth_points_ledger (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,client_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 business_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,delta BIGINT NOT NULL,balance BIGINT NOT NULL,reason VARCHAR(200) NOT NULL,created_at DATETIME NOT NULL,
 UNIQUE KEY uk_points_business(user_id,client_code,business_key),INDEX idx_points_user(user_id,id),CONSTRAINT chk_ledger_balance CHECK(balance>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
