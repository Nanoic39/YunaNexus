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
    `avatar_uuid` VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  COMMENT '用户头像(外显,储存头像文件的文件uuid)',
    `gender` VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '未知' COMMENT '用户性别(未知 / 男 / 女 / {用户输入})',
    `birthday` DATE NULL DEFAULT NULL COMMENT '用户出生日期(默认不外显)',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '用户信息更新时间戳',
    -- 不需要创建时间戳，因为用户信息表创建时间一定与用户表创建时间相同
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_user_id` (`user_id` ASC) USING BTREE,
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户信息表' ROW_FORMAT = Dynamic;

-- 资源表（标识所有接口/菜单/数据实体）
DROP TABLE IF EXISTS `resources`;
CREATE TABLE `resources` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '资源表主键id',
    `name` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源名称(唯一)',
    `code` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源标识(唯一，如：sys:menu | sys:menu:list)',
    `type` VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型( MENU | API | DATA | COMPONENT )',
    `parent_id` BIGINT NULL DEFAULT NULL COMMENT '父资源ID(用于递归表示资源层级)',
    `path` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '接口路径/路由(如果类型为 MENU | API | DATA )',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '资源状态(0：禁用，1：启用)',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_name_code` (`name` ASC, `code` ASC) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资源表' ROW_FORMAT = Dynamic;

-- 资源字段表（实现数据库字段级权限）
DROP TABLE IF EXISTS `resource_fields`;
CREATE TABLE `resource_fields` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '资源字段表主键id',
    `resource_id` BIGINT NOT NULL COMMENT '资源表外键id(关联resources表的id字段)', -- 不能唯一，因为资源可以有多个字段
    `field_name` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段名称(唯一，如：email | username | password | nickname)',
    `description` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '字段描述',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '资源字段状态(0：禁用，1：启用)',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_resource_id` (`resource_id` ASC) USING BTREE,
    FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资源字段表' ROW_FORMAT = Dynamic;

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
  INDEX `idx_end_time`(`end_time` ASC) USING BTREE,
  FOREIGN KEY (`ban_target`) REFERENCES `resources` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
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

-- 角色表
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色表主键id(不对外公开,仅用于系统内部操作)',
    `name` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称(唯一)',
    `level` INT NOT NULL DEFAULT 1 COMMENT '角色等级（1-5：普通用户，6-8：管理员，9：超级管理员）',
    `parent_role_id` BIGINT NULL DEFAULT NULL COMMENT '父角色ID(用于继承角色权限)',
    `description` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '角色描述',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '角色创建时间戳',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '角色更新时间戳',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '角色状态(0：禁用，1：启用)',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_name` (`name` ASC) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色表' ROW_FORMAT = Dynamic;

-- 用户角色关联表
DROP TABLE IF EXISTS `related_users_roles`;
CREATE TABLE `related_users_roles` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户角色关联表主键id(不对外公开，仅用于系统内部操作)',
    `user_id` BIGINT NOT NULL COMMENT '用户表外键id(关联users表的id字段)', -- 不能唯一，因为用户可以有多个角色
    `role_id` BIGINT NOT NULL COMMENT '角色表外键id(关联roles表的id字段)', -- 不能唯一，因为角色可以被多个用户拥有
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '关联状态(0：禁用，1：启用)',
    PRIMARY KEY (`id`) USING BTREE,
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关联表' ROW_FORMAT = Dynamic;

-- 数据权限规则表（实现数据库行级权限）
DROP TABLE IF EXISTS `data_rules`;
CREATE TABLE `data_rules` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据权限规则表主键id',
    `name` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称(唯一)',
    `code` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则编码( ALL | SELF | ROLE | CUSTOM )',
    `sql` VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '自定义规则SQL表达式',
    `description` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '规则描述',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '启用状态(0：禁用，1：启用)',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '规则创建时间戳',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '规则更新时间戳',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_name` (`name` ASC) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '数据权限规则表' ROW_FORMAT = Dynamic;

-- 角色权限关联表（角色+资源+字段+行级规则）
DROP TABLE IF EXISTS `related_roles_resources_fields_rules`;
CREATE TABLE `related_roles_resources_fields_rules` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色权限关联表主键id(不对外公开，仅用于系统内部操作)',
    `role_id` BIGINT NOT NULL COMMENT '角色表外键id(关联roles表的id字段)', -- 不能唯一，因为角色可以被多个资源拥有
    `resource_id` BIGINT NOT NULL COMMENT '资源表外键id(关联resources表的id字段)', -- 不能唯一，因为资源可以被多个角色拥有
    `field_ids` VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '*' COMMENT '字段ID列表(多个字段ID用英文逗号分隔，*表示所有字段)',
    `rule_id` BIGINT NOT NULL COMMENT '规则表外键id(关联data_rules表的id字段)', -- 不能唯一，因为资源可以有多个规则
    `description` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '角色权限关联描述',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '关联状态(0：禁用，1：启用)',
    PRIMARY KEY (`id`) USING BTREE,
    FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (`rule_id`) REFERENCES `data_rules` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色权限关联表' ROW_FORMAT = Dynamic;

-- 角色继承约束表
DROP TABLE IF EXISTS `related_roles_constraint`;
CREATE TABLE `related_roles_constraint` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色继承约束表主键id',
    `role_id` BIGINT NOT NULL COMMENT '角色表外键id(关联roles表的id字段)', -- 不能唯一，因为角色可以被多个资源拥有
    `exclude_role_id` BIGINT NOT NULL COMMENT '互斥角色表外键id(关联roles表的id字段)', -- 不能唯一，因为角色可以有多个互斥角色
    `type` VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'EXCLUDE' COMMENT '约束类型( EXCLUDE | INHERIT )', -- EXCLUDE：互斥角色，INHERIT：继承角色权限
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '启用状态(0：禁用，1：启用)',
    PRIMARY KEY (`id`) USING BTREE,
    FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (`exclude_role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色继承约束表' ROW_FORMAT = Dynamic;

-- OAuth2.0客户端配置表（管理所有接入统一认证的客户端（Web前端、App、第三方应用））
DROP TABLE IF EXISTS `oauth_clients`;
CREATE TABLE `oauth_clients` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'OAuth2.0客户端配置表主键id',
    `uuid` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户端配置UUID(唯一)',
    `user_id` BIGINT NOT NULL COMMENT '创建该OAuth客户端配置的用户id(关联users表的id字段)', -- 不能唯一，因为用户可以有多个客户端配置
    `client_type` tinyint NOT NULL DEFAULT 1 COMMENT '客户端类型(1：官方客户端，2：第三方应用)',
    `audit_status` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态(0：待审核，1：已通过，2：已拒绝)', -- 仅第三方应用需要审核，官方默认为已通过
    `audit_opinion` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '审核意见', -- 仅第三方应用需要填写，官方默认为"官方应用【{该OAuth客户端配置创建人UUID}】"
    `redirect_whitelist` TEXT NULL COMMENT '可访问的重定向URI列表(多个重定向URI用英文逗号分隔)', -- 第三方申请时填写，
    `client_secret` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户端密钥(唯一，存储时使用Argon2/BCrypt加密存储)',
    `client_name` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户端名称(唯一)',
    `resource_ids` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'core' COMMENT '可访问的资源ID列表(多个资源ID用英文逗号分隔)',
    `scope_limit` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'core:read,service:file:download' COMMENT '权限范围限制(多个权限范围用英文逗号分隔：{系统名称} | service:{模块名称}:read)',

    -- 授权模式（password=密码登录, refresh_token=刷新Token, client_credentials=客户端模式, authorization_code=SSO单点登录模式）、
    -- core默认为：password,refresh_token
    -- 官方客户端默认为：authorization_code,password,refresh_token
    -- 第三方应用只能为：refresh_token,client_credentials
    authorized_grant_types VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '授权模式列表(多个授权模式用英文逗号分隔：password | refresh_token | authorization_code)',

    scope VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'all' COMMENT '权限范围(多个权限范围用英文逗号分隔：all | read | write | {模块名称})',
    redirect_uri VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '回调地址（网页授权用）', -- 第三方申请时需要符合白名单

    access_token_validity INT DEFAULT 7200 COMMENT 'Access Token有效期（秒）', -- 默认2小时
    refresh_token_validity INT DEFAULT 604800 COMMENT 'Refresh Token有效期（秒）', -- 默认7天

    -- 是否允许自动授权/无需手动点击授权按钮（true=允许，false=不允许）
    -- 自动授权：用户跳转到授权页 → 不用点「确认授权」，系统直接通过，自动跳回业务端
    -- 手动授权：用户必须手动点击「确认授权」，才会发放授权码，否则拒绝授权
    auto_approve tinyint NOT NULL DEFAULT 0 COMMENT '是否无需手动点击授权按钮（仅官方客户端支持修改，0：否，1：是）', -- 默认不允许自动授权，仅官方客户端支持修改

    -- 绑定系统角色：客户端登录后自动拥有的角色（实现端级别权限隔离）
    default_role_id BIGINT NULL COMMENT '客户端登录后默认绑定的角色ID',

    `status` TINYINT DEFAULT 1 COMMENT '状态 0-禁用 1-启用',

    create_time timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_name_secret` (`client_name` ASC, `client_secret` ASC) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色继承约束表' ROW_FORMAT = Dynamic;

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