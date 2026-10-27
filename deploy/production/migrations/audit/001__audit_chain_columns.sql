-- 审计防篡改证据链（A 合规最小集）
--
-- 每条审计记录携带"上一条的摘要"与"自身摘要"，形成 per-shard 单向链：
-- 任何一条被改写/删除，其后所有记录的 prev_hash 校验都会失败，篡改位置可精确定位。
-- 链首的 prev_hash 为 64 个 0（GENESIS）。
--
-- 幂等写法：MySQL 8 的 ADD COLUMN 不支持 IF NOT EXISTS，故先查 information_schema
-- 再决定是否执行 ALTER。重复应用、或某分片已手工加过列，都不会中断迁移。
SET @add_cols := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE audit_log ADD COLUMN prev_hash CHAR(64) NOT NULL DEFAULT "" COMMENT "上一条记录摘要，链首为 64 个 0", ADD COLUMN record_hash CHAR(64) NOT NULL DEFAULT "" COMMENT "本条记录摘要 = SHA256(prev_hash + 规范化字段)"',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_log' AND COLUMN_NAME = 'prev_hash'
);
PREPARE stmt FROM @add_cols;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 链查询索引（取链头 / 区间校验）
SET @add_idx := (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_audit_chain ON audit_log (shard_index, id, record_hash)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_log' AND INDEX_NAME = 'idx_audit_chain'
);
PREPARE stmt2 FROM @add_idx;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
