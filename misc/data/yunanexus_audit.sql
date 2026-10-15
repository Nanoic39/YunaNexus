/*
 数据库名称：yunanexus_audit_0 / yunanexus_audit_1 ...（分库）
 审计中心对应数据库；逻辑分片 0~15 按 user_id % 逻辑分片数 得到 shard_index，
 再按 shard_index % 物理库数 映射到物理库（ADR-004 自研分库分表）。
 表内按 create_time 月度 RANGE 分区（TO_DAYS），支持「滚动新增分区 + 删除过期分区 + 冷备」。

 说明：
  - 分片键 user_id 为 0 时表示「未认证 / 匿名操作」（如登录失败），统一落 0 号分片。
  - 分区表要求分区列进入每个唯一键，故主键为 (id, create_time)。
  - 归档任务（AuditArchiveTask）会按月滚动补齐未来分区并删除超期分区，此处预置一段即可。

 数据库字符集：utf8mb4
 数据库排序规则：utf8mb4_unicode_ci
 */
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============ 物理库 0 ============
CREATE DATABASE IF NOT EXISTS `yunanexus_audit_0` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `yunanexus_audit_0`;

-- 审计操作日志表（按 create_time 月度分区）
CREATE TABLE IF NOT EXISTS `audit_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id` bigint NOT NULL DEFAULT '0' COMMENT '操作者用户ID(分库键)；0 表示未认证/匿名',
    `shard_index` int(11) NOT NULL COMMENT '逻辑分片编号(0~15)，由 user_id 决策',
    `username` varchar(64) DEFAULT NULL COMMENT '操作者账号名',
    `service` varchar(64) NOT NULL COMMENT '来源服务(如 yunanexus-auth)',
    `module` varchar(64) DEFAULT NULL COMMENT '业务模块',
    `action` varchar(64) NOT NULL COMMENT '操作动作(如 login/create/delete)',
    `method` varchar(16) DEFAULT NULL COMMENT 'HTTP 方法',
    `uri` varchar(512) DEFAULT NULL COMMENT '请求路径',
    `params` text COMMENT '请求参数(脱敏后，JSON 文本)',
    `result` text COMMENT '响应结果摘要',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '结果状态：1-成功 0-失败',
    `error_msg` varchar(512) DEFAULT NULL COMMENT '失败原因',
    `ip` varchar(64) DEFAULT NULL COMMENT '客户端 IP',
    `user_agent` varchar(512) DEFAULT NULL COMMENT 'User-Agent',
    `duration_ms` bigint DEFAULT NULL COMMENT '耗时(毫秒)',
    `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪 ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间(分区键)',
    PRIMARY KEY (`id`, `create_time`),
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_service_time` (`service`, `create_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计操作日志表'
PARTITION BY RANGE (TO_DAYS(`create_time`)) (
    PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')),
    PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')),
    PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')),
    PARTITION p202701 VALUES LESS THAN (TO_DAYS('2027-02-01')),
    PARTITION p202702 VALUES LESS THAN (TO_DAYS('2027-03-01')),
    PARTITION p202703 VALUES LESS THAN (TO_DAYS('2027-04-01')),
    PARTITION p202704 VALUES LESS THAN (TO_DAYS('2027-05-01')),
    PARTITION p202705 VALUES LESS THAN (TO_DAYS('2027-06-01')),
    PARTITION p202706 VALUES LESS THAN (TO_DAYS('2027-07-01')),
    PARTITION pmax VALUES LESS THAN MAXVALUE
);

-- ============ 物理库 1 ============
CREATE DATABASE IF NOT EXISTS `yunanexus_audit_1` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `yunanexus_audit_1`;

-- 审计操作日志表（按 create_time 月度分区）
CREATE TABLE IF NOT EXISTS `audit_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id` bigint NOT NULL DEFAULT '0' COMMENT '操作者用户ID(分库键)；0 表示未认证/匿名',
    `shard_index` int(11) NOT NULL COMMENT '逻辑分片编号(0~15)，由 user_id 决策',
    `username` varchar(64) DEFAULT NULL COMMENT '操作者账号名',
    `service` varchar(64) NOT NULL COMMENT '来源服务(如 yunanexus-auth)',
    `module` varchar(64) DEFAULT NULL COMMENT '业务模块',
    `action` varchar(64) NOT NULL COMMENT '操作动作(如 login/create/delete)',
    `method` varchar(16) DEFAULT NULL COMMENT 'HTTP 方法',
    `uri` varchar(512) DEFAULT NULL COMMENT '请求路径',
    `params` text COMMENT '请求参数(脱敏后，JSON 文本)',
    `result` text COMMENT '响应结果摘要',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '结果状态：1-成功 0-失败',
    `error_msg` varchar(512) DEFAULT NULL COMMENT '失败原因',
    `ip` varchar(64) DEFAULT NULL COMMENT '客户端 IP',
    `user_agent` varchar(512) DEFAULT NULL COMMENT 'User-Agent',
    `duration_ms` bigint DEFAULT NULL COMMENT '耗时(毫秒)',
    `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪 ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间(分区键)',
    PRIMARY KEY (`id`, `create_time`),
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_service_time` (`service`, `create_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计操作日志表'
PARTITION BY RANGE (TO_DAYS(`create_time`)) (
    PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')),
    PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')),
    PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')),
    PARTITION p202701 VALUES LESS THAN (TO_DAYS('2027-02-01')),
    PARTITION p202702 VALUES LESS THAN (TO_DAYS('2027-03-01')),
    PARTITION p202703 VALUES LESS THAN (TO_DAYS('2027-04-01')),
    PARTITION p202704 VALUES LESS THAN (TO_DAYS('2027-05-01')),
    PARTITION p202705 VALUES LESS THAN (TO_DAYS('2027-06-01')),
    PARTITION p202706 VALUES LESS THAN (TO_DAYS('2027-07-01')),
    PARTITION pmax VALUES LESS THAN MAXVALUE
);

SET FOREIGN_KEY_CHECKS = 1;
