-- 通知偏好（方案 B：体验优先）
--
-- 用户可按"通道 + 模板类别"关闭通知，并设置免打扰时段。
-- 免打扰时段内的通知不丢弃，而是标记为 suppressed 并顺延到窗口结束（由调度重投）。
CREATE TABLE IF NOT EXISTS notice_preference (
    user_id      BIGINT      NOT NULL COMMENT '用户 ID',
    channel      INT         NOT NULL COMMENT '通道码，与 NoticeChannel 一致',
    category     VARCHAR(64) NOT NULL COMMENT '模板代码或类别；* 表示该通道全部',
    enabled      TINYINT     NOT NULL DEFAULT 1 COMMENT '是否接收',
    quiet_start  INT         NULL COMMENT '免打扰开始（当天分钟数，如 1320=22:00）',
    quiet_end    INT         NULL COMMENT '免打扰结束（当天分钟数）',
    updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, channel, category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '通知偏好与免打扰';
