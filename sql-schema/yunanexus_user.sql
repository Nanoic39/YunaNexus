/*
 数据库名称：yunanexus_user
 数据库字符集：utf8mb4
 数据库排序规则：utf8mb4_unicode_ci
 */
-- 数据库基本设置
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 用户表
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键ID, 用于UUID解析后走索引快速查询用户信息',
    `uuid` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户UUID(外显, 用于系统内外传递用户信息)' ,
    `global_id` BINARY(16) NOT NULL COMMENT '全局ID(用于系统内各服务透传操作用户, 全局ID)',
    `route_version` TINYINT NOT NULL DEFAULT 0 COMMENT '路由版本(用于路由选择, 默认0)',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号创建时间戳',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_uuid` (`uuid` ASC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户信息表
DROP TABLE IF EXISTS `user_profile`;
CREATE TABLE `user_profile` (
    `global_id` BINARY(16) NOT NULL COMMENT '全局ID(用于系统内各服务透传操作用户, 全局ID)',
    `nickname` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户昵称',
    `avatar_uuid` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '用户头像文件uuid',
    `gender` VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户性别(未知 / 男 / 女 / {用户输入})',
    `birthday` DATE COMMENT '用户出生日期(不外显)',
    `bio` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '用户个人简介',
    `showcase_badges` JSON DEFAULT NULL COMMENT '展示徽章ID列表([{badge_id:uuid, sort:0}], 最多5个)',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号创建时间戳',
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号更新时间戳', 
    PRIMARY KEY (`global_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户经济表
DROP TABLE IF EXISTS `user_economy`;
CREATE TABLE `user_economy` (
    `global_id` BINARY(16) NOT NULL COMMENT '全局ID(用于系统内各服务透传操作用户, 全局ID)',
    `exp` BIGINT NOT NULL DEFAULT 0 COMMENT '用户经验值',
    `coin` BIGINT NOT NULL DEFAULT 0 COMMENT '用户货币(金币)数量',
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号更新时间戳', 
    PRIMARY KEY (`global_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--

SET FOREIGN_KEY_CHECKS = 1;