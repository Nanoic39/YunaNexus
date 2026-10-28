-- 通知去重窗口（方案 B：频控）
--
-- 同一用户 + 同一模板在一个窗口内只投递一次，避免事件重放/重试造成重复通知。
-- 用唯一键冲突来判断"本窗口已发过"，不需要先查再写，避免并发下双发。
CREATE TABLE IF NOT EXISTS notice_dedup (
    user_id       BIGINT      NOT NULL,
    category      VARCHAR(64) NOT NULL,
    window_start  BIGINT      NOT NULL COMMENT '窗口起点（epoch 毫秒）',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, category, window_start)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '通知去重窗口';
