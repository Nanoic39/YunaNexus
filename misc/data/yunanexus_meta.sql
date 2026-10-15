/*
 数据库名称：yunanexus_meta
 元数据管理中心对应数据库
 数据库字符集：utf8mb4
 数据库排序规则：utf8mb4_unicode_ci
 */
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `yunanexus_meta` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `yunanexus_meta`;

-- 号段表
CREATE TABLE IF NOT EXISTS `id_segment` (
  `biz_tag` varchar(128) NOT NULL COMMENT '业务标识：auth/file/notice',
  `max_id` bigint NOT NULL COMMENT '当前已分配的最大ID值',
  `step` int(11) NOT NULL COMMENT '每次领取的号段步长（动态步长，范围 1000-10000）',
  `version` int(11) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，用于防止并发冲突',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `description` varchar(255) DEFAULT NULL COMMENT '业务描述',
  PRIMARY KEY (`biz_tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='号段配置表';

-- 号段种子数据：各业务标识的初始配置（step 为动态步长的初始值）
INSERT IGNORE INTO `id_segment` (`biz_tag`, `max_id`, `step`, `version`, `description`) VALUES
    ('user',   0, 2000, 0, '用户中心：全局用户 ID'),
    ('file',   0, 2000, 0, '文件中心：文件 ID'),
    ('notice', 0, 2000, 0, '通知中心：通知记录 ID'),
    ('audit',  0, 2000, 0, '审计中心：审计日志 ID');

SET FOREIGN_KEY_CHECKS = 1;