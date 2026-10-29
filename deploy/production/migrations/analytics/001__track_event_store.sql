-- 埋点事件存储（方案 C：先 MySQL，抽象层预留 ClickHouse）
--
-- 此前事件只进 MQ 无人落库（0 实体 0 Mapper），分析无数据可依。
-- 保留 90 天，索引围绕"时间范围 / 事件类型 / 用户"三类查询。
CREATE TABLE IF NOT EXISTS track_event (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    event_id     VARCHAR(64)  NOT NULL COMMENT '事件唯一 ID',
    event_type   VARCHAR(64)  NOT NULL COMMENT '事件类型',
    event_time   DATETIME(3)  NOT NULL COMMENT '事件发生时间',
    anonymous_id VARCHAR(64)  NULL,
    user_id      VARCHAR(64)  NULL COMMENT '外部用户 ID',
    session_id   VARCHAR(64)  NULL,
    device_id    VARCHAR(64)  NULL,
    page_url     VARCHAR(512) NULL,
    referrer     VARCHAR(512) NULL,
    client_ip    VARCHAR(64)  NULL,
    user_agent   VARCHAR(512) NULL,
    props        TEXT         NULL COMMENT '自定义属性 JSON',
    create_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_track_event_id (event_id),
    KEY idx_track_time (event_time),
    KEY idx_track_type_time (event_type, event_time),
    KEY idx_track_user_time (user_id, event_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '埋点事件（保留 90 天）';
