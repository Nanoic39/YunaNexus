/*
 数据库名称：yunanexus_auth
 元数据管理中心对应数据库
 数据库字符集：utf8mb4
 数据库排序规则：utf8mb4_unicode_ci
 */
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `yunanexus_auth` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `yunanexus_auth`;

-- 用户凭证表
CREATE TABLE IF NOT EXISTS `auth_user` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id` bigint NOT NULL COMMENT '全局用户ID(meta分配,不包含分库信息)',
    `shard_index` int(11) NOT NULL COMMENT '逻辑分片编号(0~15)，用于生成外部ID和路由',
    `external_id` varchar(64) NOT NULL COMMENT '外部ID(注册时encode一次落库,只读不重算,UNIQUE)',
    `username` varchar(64) NOT NULL COMMENT '账号',
    `email` varchar(128) DEFAULT NULL COMMENT '邮箱(可选)',
    `mobile_country` varchar(10) DEFAULT NULL COMMENT '国家号(手机号国家标识，手机号存在时默认+86，否则为NULL)',
    `mobile` varchar(20) DEFAULT NULL COMMENT '手机号(可选，登录凭证之一)',
    `password` varchar(255) NOT NULL COMMENT '加密后的密码',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '账号状态：1-正常 0-禁用 2-锁定',
    `locked_until` datetime DEFAULT NULL COMMENT '锁定截止时间',
    `lock_reason` varchar(255) DEFAULT NULL COMMENT '锁定原因',
    `fail_count` int(11) NOT NULL DEFAULT '0' COMMENT '连续登录失败计数(登录成功后清零)',
    `last_fail_time` datetime DEFAULT NULL COMMENT '最后一次登录失败时间',
    `last_login_time` datetime DEFAULT NULL COMMENT '最后一次成功登录时间',
    `last_login_ip` varchar(64) DEFAULT NULL COMMENT '最后一次成功登录IP',
    `password_update_time` datetime DEFAULT NULL COMMENT '密码最后修改时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    UNIQUE KEY `uk_external_id` (`external_id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_mobile` (`mobile_country`, `mobile`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证用户凭证表';

-- 客户端表
CREATE TABLE IF NOT EXISTS `auth_client` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `client_code` varchar(64) NOT NULL COMMENT '客户端唯一标识',
    `client_name` varchar(128) NOT NULL COMMENT '客户端名称, 展示给用户',
    `redirect_uri` varchar(500) NOT NULL COMMENT 'SSO回调地址, 登录成功后跳回的地址',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '启用状态：1-启用 0-禁用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_client_code` (`client_code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户端表';

-- 授权记录表
CREATE TABLE IF NOT EXISTS `auth_platform` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id` bigint(20) NOT NULL COMMENT '全局用户ID',
    `client_code` varchar(64) NOT NULL COMMENT '客户端标识, 关联client_code',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '关联状态：1-正常/启用 2-停用 0-已撤销',
    `authorized_at` datetime DEFAULT NULL COMMENT '用户授权时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_client` (`user_id`, `client_code`),
    KEY `idx_client_code` (`client_code`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授权记录表';

-- 客户端权限关联表
CREATE TABLE IF NOT EXISTS `auth_client_scope` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `client_code` varchar(64) NOT NULL COMMENT 'auth_client表客户端标识',
    `permission_id` bigint(20) NOT NULL COMMENT '权限ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_client_permission` (`client_code`, `permission_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户端权限关联表';

-- 登录尝试记录表
CREATE TABLE IF NOT EXISTS `auth_login_attempt` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id` bigint DEFAULT NULL COMMENT '全局用户ID，账号不存在时为空',
    `username` varchar(64) NOT NULL COMMENT '尝试登录的账号',
    `login_type` varchar(32) NOT NULL DEFAULT 'password' COMMENT '登录方式：password / refresh_token / sso',
    `success` tinyint(1) NOT NULL COMMENT '是否成功：1-成功 0-失败',
    `fail_reason` varchar(64) DEFAULT NULL COMMENT '失败原因：BAD_CREDENTIALS / USER_LOCKED / USER_DISABLED / CAPTCHA_REQUIRED / USER_NOT_FOUND',
    `ip` varchar(64) DEFAULT NULL COMMENT '客户端IP',
    `user_agent` varchar(500) DEFAULT NULL COMMENT '客户端User-Agent',
    `device_id` varchar(128) DEFAULT NULL COMMENT '设备指纹',
    `country` varchar(64) DEFAULT NULL COMMENT '国家',
    `province` varchar(64) DEFAULT NULL COMMENT '省份',
    `city` varchar(64) DEFAULT NULL COMMENT '城市',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '尝试时间',
    PRIMARY KEY (`id`),
    KEY `idx_username` (`username`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_ip` (`ip`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录尝试记录表';

-- 账户解锁申诉表
CREATE TABLE IF NOT EXISTS `auth_unlock_appeal` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id` bigint(20) DEFAULT NULL COMMENT '全局用户ID, 账号不存在时为空',
    `username` varchar(64) NOT NULL COMMENT '被锁定的账号',
    `appeal_reason` varchar(500) DEFAULT NULL COMMENT '用户提交的申诉说明',
    `contact` varchar(128) DEFAULT NULL COMMENT '联系方式(邮箱/手机号)，用于反馈处理结果',
    `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '处理状态：0-待处理 1-已通过 2-已拒绝',
    `auditor` varchar(64) DEFAULT NULL COMMENT '处理人账号',
    `audit_remark` varchar(500) DEFAULT NULL COMMENT '处理意见/备注',
    `audit_time` datetime DEFAULT NULL COMMENT '处理时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申诉提交时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_username` (`username`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户解锁申诉表';

-- 注册本地消息表（outbox，ADR-021）
-- 与 auth_user 在同一本地事务内写入；后台任务扫描 PENDING 记录调用用户中心创建资料，
-- 成功置 DONE，失败按指数退避重试，超过上限置 FAILED 并告警。唯一键 user_id 保证幂等。
CREATE TABLE IF NOT EXISTS `auth_register_outbox` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id` bigint(20) NOT NULL COMMENT '全局用户ID(与 auth_user.user_id 一致，唯一)',
    `external_id` varchar(64) NOT NULL COMMENT '外部ID(注册时生成，落库只读)',
    `shard_index` int(11) NOT NULL COMMENT '逻辑分片编号(0~15)，随注册一并下发用户中心',
    `username` varchar(64) NOT NULL COMMENT '账号',
    `email` varchar(128) DEFAULT NULL COMMENT '邮箱(可选)',
    `mobile_country` varchar(10) DEFAULT NULL COMMENT '国家号',
    `mobile` varchar(20) DEFAULT NULL COMMENT '手机号(可选)',
    `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '同步状态：0-待同步(PENDING) 1-已同步(DONE) 2-同步失败(FAILED,超重试上限)',
    `retry_count` int(11) NOT NULL DEFAULT '0' COMMENT '已重试次数',
    `next_retry_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间(指数退避)',
    `last_error` varchar(500) DEFAULT NULL COMMENT '最后一次失败原因(脱敏，不含敏感信息)',
    `synced_time` datetime DEFAULT NULL COMMENT '同步成功时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    KEY `idx_status_next_retry` (`status`, `next_retry_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='注册本地消息表(outbox，ADR-021)';
-- 个人工作空间（2026-10-15）
USE yunanexus_auth;
CREATE TABLE IF NOT EXISTS auth_personal_api_key (
 id VARCHAR(32) PRIMARY KEY, user_id BIGINT NOT NULL, name VARCHAR(64) NOT NULL,
 token_prefix VARCHAR(16) NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE,
 scopes VARCHAR(256) NOT NULL, created_at DATETIME NOT NULL, expires_at DATETIME NOT NULL,
 last_used_at DATETIME NULL, revoked TINYINT NOT NULL DEFAULT 0,
 INDEX idx_owner(user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
