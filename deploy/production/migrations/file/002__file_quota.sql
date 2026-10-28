-- 文件存储配额（A1）
--
-- 按用户维度记录已用容量与文件数。配额行放在该用户所属逻辑分片的库中：
-- 文件本体按 fileId 分片，配额按 userId 分片，两者互不影响。
CREATE TABLE IF NOT EXISTS file_quota (
    user_id     BIGINT      NOT NULL COMMENT '用户 ID',
    used_bytes  BIGINT      NOT NULL DEFAULT 0 COMMENT '已用容量（字节）',
    file_count  BIGINT      NOT NULL DEFAULT 0 COMMENT '已用文件数',
    limit_bytes BIGINT      NOT NULL DEFAULT 0 COMMENT '容量上限；0 表示使用全局默认',
    limit_files BIGINT      NOT NULL DEFAULT 0 COMMENT '文件数上限；0 表示使用全局默认',
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户存储配额';
