-- 恶意文件扫描状态（A5）
--
-- pending：待扫描（扫描服务不可用时也落这个状态，fail-open 但可追溯）
-- clean  ：扫描通过
-- infected：检出恶意内容，文件已被隔离
ALTER TABLE file_meta
    ADD COLUMN scan_status VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/clean/infected';

CREATE INDEX idx_file_meta_scan ON file_meta (scan_status, create_time);
