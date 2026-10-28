-- 上传限流改为库内共享计数（不再随实例数放大）
--
-- 原实现是各实例内存计数：N 个实例 → 实际上限变成 N 倍。
-- 改为按 (限流维度, 窗口起点) 存一行，用原子 UPSERT 累加，所有实例共享同一计数。
CREATE TABLE IF NOT EXISTS upload_rate (
    rate_key     VARCHAR(128) NOT NULL COMMENT '限流键，如 u:123 / ip:1.2.3.4',
    window_start BIGINT       NOT NULL COMMENT '窗口起点（epoch 毫秒）',
    hits         INT          NOT NULL DEFAULT 0 COMMENT '窗口内次数',
    PRIMARY KEY (rate_key, window_start)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '上传限流共享计数';
