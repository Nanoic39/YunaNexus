-- 埋点限流的共享计数（多实例一致）
--
-- 原实现是各实例内存计数：N 个实例 → 实际上限变成 N 倍，限流形同虚设。
CREATE TABLE IF NOT EXISTS track_rate (
    window_second BIGINT NOT NULL COMMENT '窗口（epoch 秒）',
    hits          BIGINT NOT NULL DEFAULT 0 COMMENT '该秒累计事件数',
    PRIMARY KEY (window_second)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '埋点限流共享计数';
