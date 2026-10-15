/*
 数据库名称：yunanexus_user_0 / yunanexus_user_1 ...（分库）
 用户中心对应数据库；逻辑分片 0~15 按 shard_index % 物理库数 映射到物理库。
 新增物理库时：追加 CREATE DATABASE 与其下的 sys_user 建表。
 数据库字符集：utf8mb4
 数据库排序规则：utf8mb4_unicode_ci
 */
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============ 物理库 0 ============
CREATE DATABASE IF NOT EXISTS `yunanexus_user_0` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `yunanexus_user_0`;

-- 用户资料表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id` bigint NOT NULL COMMENT '全局用户ID(meta分配,不包含分库信息)',
    `shard_index` int(11) NOT NULL COMMENT '逻辑分片编号(0~15)，注册时由认证中心决策',
    `external_id` varchar(64) NOT NULL COMMENT '外部ID(注册时编码一次落库,此后只读)',
    `username` varchar(64) NOT NULL COMMENT '账号',
    `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
    `avatar` varchar(512) DEFAULT NULL COMMENT '头像URL',
    `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
    `mobile_country` varchar(8) DEFAULT NULL COMMENT '手机号国家号',
    `mobile` varchar(32) DEFAULT NULL COMMENT '手机号',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-正常 0-禁用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_external_id` (`external_id`),
    KEY `idx_email` (`email`),
    KEY `idx_mobile` (`mobile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户资料表';

-- 用户标签表（自助维护，按 user_id 分片）
CREATE TABLE IF NOT EXISTS `user_tag` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id` bigint NOT NULL COMMENT '全局用户ID',
    `shard_index` int(11) NOT NULL COMMENT '逻辑分片编号(0~15)',
    `tag_code` varchar(32) NOT NULL COMMENT '标签编码(用户内唯一)',
    `tag_name` varchar(32) NOT NULL COMMENT '标签名称',
    `color` varchar(16) DEFAULT NULL COMMENT '标签颜色(前端展示)',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tag` (`user_id`, `tag_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户标签表';

-- 用户徽章表（成就，系统授予，按 user_id 分片）
CREATE TABLE IF NOT EXISTS `user_badge` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id` bigint NOT NULL COMMENT '全局用户ID',
    `shard_index` int(11) NOT NULL COMMENT '逻辑分片编号(0~15)',
    `badge_code` varchar(64) NOT NULL COMMENT '徽章编码(用户内唯一)',
    `badge_name` varchar(64) NOT NULL COMMENT '徽章名称',
    `badge_level` tinyint(4) NOT NULL DEFAULT '1' COMMENT '徽章等级(1~9)',
    `description` varchar(255) DEFAULT NULL COMMENT '徽章描述',
    `icon` varchar(512) DEFAULT NULL COMMENT '徽章图标URL',
    `granted_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授予时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_badge` (`user_id`, `badge_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户徽章表';

-- ============ 物理库 1 ============
CREATE DATABASE IF NOT EXISTS `yunanexus_user_1` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `yunanexus_user_1`;

-- 用户资料表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id` bigint NOT NULL COMMENT '全局用户ID(meta分配,不包含分库信息)',
    `shard_index` int(11) NOT NULL COMMENT '逻辑分片编号(0~15)，注册时由认证中心决策',
    `external_id` varchar(64) NOT NULL COMMENT '外部ID(注册时编码一次落库,此后只读)',
    `username` varchar(64) NOT NULL COMMENT '账号',
    `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
    `avatar` varchar(512) DEFAULT NULL COMMENT '头像URL',
    `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
    `mobile_country` varchar(8) DEFAULT NULL COMMENT '手机号国家号',
    `mobile` varchar(32) DEFAULT NULL COMMENT '手机号',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-正常 0-禁用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_external_id` (`external_id`),
    KEY `idx_email` (`email`),
    KEY `idx_mobile` (`mobile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户资料表';

-- 用户标签表（自助维护，按 user_id 分片）
CREATE TABLE IF NOT EXISTS `user_tag` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id` bigint NOT NULL COMMENT '全局用户ID',
    `shard_index` int(11) NOT NULL COMMENT '逻辑分片编号(0~15)',
    `tag_code` varchar(32) NOT NULL COMMENT '标签编码(用户内唯一)',
    `tag_name` varchar(32) NOT NULL COMMENT '标签名称',
    `color` varchar(16) DEFAULT NULL COMMENT '标签颜色(前端展示)',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tag` (`user_id`, `tag_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户标签表';

-- 用户徽章表（成就，系统授予，按 user_id 分片）
CREATE TABLE IF NOT EXISTS `user_badge` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id` bigint NOT NULL COMMENT '全局用户ID',
    `shard_index` int(11) NOT NULL COMMENT '逻辑分片编号(0~15)',
    `badge_code` varchar(64) NOT NULL COMMENT '徽章编码(用户内唯一)',
    `badge_name` varchar(64) NOT NULL COMMENT '徽章名称',
    `badge_level` tinyint(4) NOT NULL DEFAULT '1' COMMENT '徽章等级(1~9)',
    `description` varchar(255) DEFAULT NULL COMMENT '徽章描述',
    `icon` varchar(512) DEFAULT NULL COMMENT '徽章图标URL',
    `granted_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授予时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_badge` (`user_id`, `badge_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户徽章表';
