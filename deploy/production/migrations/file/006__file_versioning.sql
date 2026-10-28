-- B 阶段：文件版本
--
-- root_id：同一逻辑文件的所有版本共享；首版 root_id = 自身 file_id
-- version：从 1 递增
-- 物理存储仍按内容共享（ref_count 已就绪），版本只增加元数据行。
ALTER TABLE file_meta
    ADD COLUMN root_id BIGINT NULL COMMENT '逻辑文件根 ID；首版等于自身 file_id',
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '版本号，从 1 递增';

UPDATE file_meta SET root_id = file_id WHERE root_id IS NULL;

CREATE INDEX idx_file_meta_root ON file_meta (root_id, version);
