/*
 数据库名称：yunanexus_auth
 数据库字符集：utf8mb4
 数据库排序规则：utf8mb4_unicode_ci
 */
-- 数据库基本设置
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 用户认证表
DROP TABLE IF EXISTS `user_identity`;
CREATE TABLE `user_identity` (
    `global_id` BINARY(16) NOT NULL COMMENT '全局ID(用于系统内各服务透传操作用户, 全局ID)',
    `username` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名/账号',
    `password` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
    `email` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '手机号',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '帐号状态(0：注销，1：正常，2：永久封禁/暂时封禁，3：用户本人冻结{需要输入账户绑定手机/邮箱收到的验证码}解冻)',
    PRIMARY KEY (`global_id`) USING BTREE,
    UNIQUE INDEX `ui_username` (`username`),
    UNIQUE INDEX `ui_email` (`email`),
    UNIQUE INDEX `ui_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- OAuth 客户端表
DROP TABLE IF EXISTS `oauth_client`;
CREATE TABLE `oauth_client` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '客户端自增主键',
    `uuid` VARCHAR(64) NOT NULL COMMENT '客户端UUID(自动生成)',
    `client_name` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户端名称，客户端请求时需要与这里数据保持一致',
    `client_secret` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '客户端密钥(加密后)',
    `client_type` TINYINT NOT NULL DEFAULT 1 COMMENT '1:官方 2:第三方',
    `grant_types` VARCHAR(255) NOT NULL COMMENT 'password,refresh_token,authorization_code',
    `scope` VARCHAR(255) NOT NULL DEFAULT 'core:*:*:*' COMMENT '授权范围',
    `redirect_uri` VARCHAR(255) DEFAULT NULL COMMENT '回调地址(第三方必填)',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '申请说明',
    `audit_status` TINYINT NOT NULL DEFAULT 1 COMMENT '0:待审核 1:已通过 2:已拒绝(官方直接=1)',
    `applicant_global_id` BINARY(16) DEFAULT NULL COMMENT '申请人(users.global_id, 第三方必填)',
    `auditor_global_id` BINARY(16) DEFAULT NULL COMMENT '审核人id',
    `audit_opinion` VARCHAR(255) DEFAULT NULL COMMENT '审核意见',
    `audited_at` TIMESTAMP NULL DEFAULT NULL COMMENT '审核时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0:禁用 1:启用',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `ui_uuid` (`uuid`),
    UNIQUE INDEX `ui_client_name` (`client_name`),
    INDEX `idx_type_audit` (`client_type`, `audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 管理员初始化密钥表
DROP TABLE IF EXISTS `admin_init_keys`;
CREATE TABLE `admin_init_keys` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `init_key` VARCHAR(64) NOT NULL COMMENT '初始化密钥(随机生成)',
    `used_by` BINARY(16) DEFAULT NULL COMMENT '使用者global_id',
    `used_at` TIMESTAMP NULL DEFAULT NULL COMMENT '使用时间',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `ui_init_key` (`init_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 角色表
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(32) NOT NULL COMMENT 'SUPER_ADMIN / ADMIN / VIP / USER',
    `level` INT NOT NULL DEFAULT 1 COMMENT '权限等级 1-99',
    `permissions` JSON NOT NULL COMMENT '权限码列表 ["*:*:*:*", "server:page:resources:action"]',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '角色状态(0: 取消，1: 启用，2: 删除)',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `ui_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户角色关联表
DROP TABLE IF EXISTS `user_roles`;
CREATE TABLE `user_roles` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `global_id` BINARY(16) NOT NULL COMMENT '全局ID(用于系统内各服务透传操作用户, 全局ID)',
    `role_id` BIGINT NOT NULL COMMENT '角色id',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '关联状态(0: 取消，1: 启用，2: 删除)',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `ui_user_role` (`global_id`, `role_id`),
    FOREIGN KEY (`role_id`) REFERENCES `roles`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 默认角色数据
INSERT INTO `roles` (`name`, `level`, `permissions`, `status`) VALUES
    ('SUPER_ADMIN', 99, '["*:*:*:*"]', 1),
    ('ADMIN', 60, '["core:*:*:manage"]', 1),
    ('USER', 1, '["core:user:profile:read"]', 1);

SET FOREIGN_KEY_CHECKS = 1;