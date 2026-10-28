-- B 阶段：共享物理存储的引用计数 + 回收站
--
-- ref_count：有多少条"有效元数据"引用同一份物理内容。归零才允许物理删除。
--            历史上靠删除时跨库扫描统计，成本随文件量线性上升；改为显式计数后是 O(1)。
-- deleted_at：进入回收站的时间，用于 30 天保留策略。
ALTER TABLE file_meta
    ADD COLUMN ref_count  INT NOT NULL DEFAULT 1 COMMENT '物理内容引用计数',
    ADD COLUMN deleted_at DATETIME(3) NULL COMMENT '进入回收站时间';

-- 回填：按当前存储路径的实际引用数校正（同一 sha256 的秒传复用会共享存储路径）
UPDATE file_meta f
    JOIN (SELECT storage_path, COUNT(*) AS c FROM file_meta WHERE status = 1 GROUP BY storage_path) t
    ON f.storage_path = t.storage_path
SET f.ref_count = t.c;

CREATE INDEX idx_file_meta_trash ON file_meta (status, deleted_at);
