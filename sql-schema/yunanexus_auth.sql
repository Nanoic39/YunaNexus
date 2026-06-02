/*
 数据库名称：yunanexus_user
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
    `phone` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '手机号',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '帐号状态(0：注销，1：正常，2：永久封禁/暂时封禁，3：用户本人冻结{需要输入账户绑定手机/邮箱收到的验证码}解冻)',
    PRIMARY KEY (`global_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;