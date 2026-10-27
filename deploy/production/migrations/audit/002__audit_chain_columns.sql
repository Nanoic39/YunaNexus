-- 审计防篡改证据链（A 合规最小集）
--
-- 每条审计记录携带"上一条的摘要"与"自身摘要"，形成 per-shard 单向链：
-- 任何一条被改写/删除，其后所有记录的 prev_hash 校验都会失败，篡改位置可精确定位。
-- 链首的 prev_hash 为 64 个 0（GENESIS）。
--
-- 注意：audit_log 为分库（16 逻辑分片 / 2 物理库）+ 按月 RANGE 分区的表，
-- 本迁移需在每个物理库上分别执行（由迁移闸门按分片批量应用）。
ALTER TABLE audit_log
    ADD COLUMN prev_hash   CHAR(64) NOT NULL DEFAULT '' COMMENT '上一条记录摘要，链首为 64 个 0',
    ADD COLUMN record_hash CHAR(64) NOT NULL DEFAULT '' COMMENT '本条记录摘要 = SHA256(prev_hash + 规范化字段)';

-- 按 (shard, id) 倒序取链头、按区间校验时使用
CREATE INDEX idx_audit_chain ON audit_log (shard_index, id, record_hash);
