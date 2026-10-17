USE yunanexus_auth;
SET @has_category=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='auth_client' AND COLUMN_NAME='application_category');
SET @ddl=IF(@has_category=0,"ALTER TABLE auth_client ADD COLUMN application_category VARCHAR(32) NOT NULL DEFAULT 'THIRD_PARTY'",'SELECT 1');
PREPARE category_stmt FROM @ddl; EXECUTE category_stmt; DEALLOCATE PREPARE category_stmt;
-- Explicit migration: never classify all legacy applications as official.
UPDATE auth_client SET application_category='OFFICIAL_NATIVE' WHERE client_code='web';
CREATE TABLE IF NOT EXISTS auth_application_trust_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,client_code VARCHAR(64) NOT NULL,
 old_category VARCHAR(32) NOT NULL,new_category VARCHAR(32) NOT NULL,actor_user_id BIGINT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX idx_trust_client(client_code,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
