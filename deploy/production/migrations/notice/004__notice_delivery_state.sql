-- 投递状态机（可靠性底座）
--
-- 原状态只有 PENDING/SENT："发出去了"与"成功送达"混为一谈，失败无痕迹。
-- 新增：失败原因、重试时间、死信标记，使"用户说没收到"时可追溯。
ALTER TABLE notice_record
    ADD COLUMN fail_reason     VARCHAR(512) NULL COMMENT '最近一次失败原因',
    ADD COLUMN next_retry_time DATETIME(3)  NULL COMMENT '下次重试时间（退避）',
    ADD COLUMN dead_letter     TINYINT      NOT NULL DEFAULT 0 COMMENT '1=已进入死信，不再重试';

CREATE INDEX idx_notice_record_retry ON notice_record (status, next_retry_time);
