/*
 数据库名称：core_yunanexus_user
 数据库字符集：utf8mb4
 数据库排序规则：utf8mb4_unicode_ci
 */
-- 数据库基本设置
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 用户表
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键id(不对外公开,仅用于系统内部操作)',
    `uuid` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户唯一标识(外显,用于系统内外传递用户信息)',
    `username` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名(唯一,用于用户登录)',
    `password` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码(存储时使用Argon2/BCrypt加密存储)',
    `email` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '电子邮箱(唯一,可用于登录和接收通知)',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '帐号状态(0：注销，1：正常，2：封禁/冻结)',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号创建时间戳',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '账号更新时间戳',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_uuid` (`uuid` ASC) USING BTREE,
    UNIQUE INDEX `ui_username` (`username` ASC) USING BTREE,
    UNIQUE INDEX `ui_email` (`email` ASC) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

-- 用户信息表
DROP TABLE IF EXISTS `user_info`;
CREATE TABLE `user_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户信息表主键id(不对外公开,仅用于系统内部操作)',
    `user_id` BIGINT NOT NULL COMMENT '用户表外键id(关联users表的id字段)',
    `nickname` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户昵称(外显)',
    `avatar_uuid` VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户头像(外显,储存头像文件的文件uuid)',
    `gender` VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '未知' COMMENT '用户性别(未知 / 男 / 女 / {用户输入})',
    `birthday` DATE NULL DEFAULT NULL COMMENT '用户出生日期(默认不外显)',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '用户信息更新时间戳',
    -- 不需要创建时间戳，因为用户信息表创建时间一定与用户表创建时间相同
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_user_id` (`user_id` ASC) USING BTREE,
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户信息表' ROW_FORMAT = Dynamic;


-- 封禁表
DROP TABLE IF EXISTS `ban_record`;
CREATE TABLE `ban_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `user_id` bigint NOT NULL COMMENT '关联user表的id(封禁用户)',
  `ban_type` tinyint NOT NULL COMMENT '封禁类型(0：警告，1：临时封禁，2：永久封禁)',
  `ban_range` tinyint NOT NULL COMMENT '封禁范围(0：指定，1：全局)',
  `ban_service` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '封禁服务范围(服务名称)',
  `ban_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '封禁原因',
  `ban_operator_id` bigint NOT NULL COMMENT '封禁操作人ID',
  `start_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '封禁开始时间戳',
  `end_time` timestamp NULL DEFAULT NULL COMMENT '封禁结束时间戳(null：永久封禁)',
  `unban_operator_id` bigint NULL DEFAULT NULL COMMENT '解封操作人ID',
  `ban_scope` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'GLOBAL' COMMENT '封禁范围: GLOBAL（整个服务）, RESOURCES（指定资源）',
  `ban_target` BIGINT NULL DEFAULT NULL COMMENT 'RESOURCES范围时填写，标识封禁的资源ID',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_end_time`(`end_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '封禁记录表' ROW_FORMAT = DYNAMIC;

-- 申诉记录表
DROP TABLE IF EXISTS `appeal_record`;
CREATE TABLE `appeal_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `user_uuid` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联user的uuid(申诉用户)',
  `reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '申诉原因',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态(0：待处理，1：处理中，2：已通过，3：已驳回)',
  `operator_id` bigint NULL DEFAULT NULL COMMENT '当前处理人ID(锁定后写入)',
  `process_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '处理备注',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间戳',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间戳',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_uuid`(`user_uuid` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '申诉记录表' ROW_FORMAT = DYNAMIC;


-- 用户与OAuth客户端关联表（仅维护用户与客户端配置UUID的单向关联，不存客户端详情）
DROP TABLE IF EXISTS `related_user_oauth_oauth_client`;
CREATE TABLE `related_user_oauth_oauth_client` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户与OAuth客户端关联表主键id',
    `user_id` BIGINT NOT NULL COMMENT '用户表外键id(关联users表的id字段)',
    `client_uuid` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OAuth客户端配置UUID(关联Auth服务oauth_clients.uuid)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '关联状态(0：禁用，1：启用)',
    `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_user_client` (`user_id` ASC, `client_uuid` ASC) USING BTREE,
    UNIQUE INDEX `ui_client_uuid` (`client_uuid` ASC) USING BTREE,
    INDEX `idx_user_status` (`user_id` ASC, `status` ASC) USING BTREE,
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户与OAuth客户端关联表' ROW_FORMAT = Dynamic;

-- TODO: 临时测试用，待删除 - 后续会有其它服务提供整体的版本管理支持
-- 版本配置表
DROP TABLE IF EXISTS `service_version`;
CREATE TABLE `service_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '服务版本配置表主键',
  `service_key` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务标识',
  `latest_version` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '最新版本号 例：1.0.0',
  `update_desc` VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新说明',
  `update_url` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '下载地址',
  `force_update` TINYINT NOT NULL DEFAULT 0 COMMENT '0不强制 1强制',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用(最新版本出现异常需要紧急回滚版本时) 1启用',
  `create_time` timestamp DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_key` (`service_key`)
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '服务版本配置表' ROW_FORMAT = Dynamic;

INSERT INTO `service_version` (`service_key`, `latest_version`, `update_desc`, `update_url`, `force_update`, `status`) VALUES ('YunaNexus-UserService', '1.0.0', '这是Yuna用户服务最初的版本!', NULL, 0, 1);


--
SET FOREIGN_KEY_CHECKS = 1;