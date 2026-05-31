/*
 数据库名称：core_yunanexus_auth
 数据库字符集：utf8mb4
 数据库排序规则：utf8mb4_unicode_ci
*/
-- 数据库基本设置
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- OAuth2.0客户端配置表（管理所有接入统一认证的客户端：Web前端、App、第三方应用）
DROP TABLE IF EXISTS `oauth_clients`;
CREATE TABLE `oauth_clients` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'OAuth2.0客户端配置表主键id',
    `uuid` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户端配置UUID(唯一,系统内部主标识)',
    `client_type` TINYINT NOT NULL DEFAULT 1 COMMENT '客户端类型(1：官方客户端，2：第三方应用)',
    `audit_status` TINYINT NOT NULL DEFAULT 0 COMMENT '审核状态(0：待审核，1：已通过，2：已拒绝)', -- 仅第三方应用需要审核，官方默认为已通过
    `audit_opinion` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '审核意见', -- 仅第三方应用需要填写，官方默认为"官方应用【{该OAuth客户端配置创建人UUID}】"
    `redirect_whitelist` TEXT NULL COMMENT '可访问的重定向URI列表(多个重定向URI用英文逗号分隔)', -- 第三方申请时填写
    `client_secret` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户端密钥(存储时使用Argon2/BCrypt加密存储)',
    `client_name` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户端名称(唯一,用于外显/输入校验)',
    `resource_ids` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'core' COMMENT '可访问的资源ID列表(多个资源ID用英文逗号分隔)',
    `scope_limit` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'core:read,service:file:download' COMMENT '权限范围限制(多个权限范围用英文逗号分隔：{系统名称} | service:{模块名称}:read)',

    -- 授权模式（password=密码登录, refresh_token=刷新Token, client_credentials=客户端模式, authorization_code=SSO单点登录模式）
    -- core默认为：password,refresh_token
    -- 官方客户端默认为：authorization_code,password,refresh_token
    -- 第三方应用只能为：authorization_code,refresh_token
    `authorized_grant_types` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '授权模式列表(多个授权模式用英文逗号分隔：password | refresh_token | client_credentials | authorization_code)',

    `scope` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'all' COMMENT '权限范围(多个权限范围用英文逗号分隔：all | read | write | {模块名称})',
    `redirect_uri` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '回调地址（网页授权用）', -- 第三方申请时需要符合白名单

    `access_token_validity` INT DEFAULT 7200 COMMENT 'Access Token有效期（秒）', -- 默认2小时
    `refresh_token_validity` INT DEFAULT 604800 COMMENT 'Refresh Token有效期（秒）', -- 默认7天

    -- 是否允许自动授权/无需手动点击授权按钮（1=允许，0=不允许）
    -- 自动授权：用户跳转到授权页 → 不用点「确认授权」，系统直接通过，自动跳回业务端
    -- 手动授权：用户必须手动点击「确认授权」，才会发放授权码，否则拒绝授权
    -- 仅在 authorization_code 授权模式下生效
    `auto_approve` TINYINT NOT NULL DEFAULT 0 COMMENT '是否无需手动点击授权按钮（仅官方客户端支持修改，0：否，1：是）', -- 默认不允许自动授权，仅官方客户端支持修改

    -- 绑定系统角色：客户端登录后自动拥有的角色（实现端级别权限隔离）
    `default_role_id` BIGINT NULL COMMENT '客户端登录后默认绑定的角色ID',

    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0-禁用 1-启用',

    `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_uuid` (`uuid` ASC) USING BTREE,
    UNIQUE INDEX `ui_client_name` (`client_name` ASC) USING BTREE,
    INDEX `idx_status_type_audit` (`status` ASC, `client_type` ASC, `audit_status` ASC) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'OAuth2.0客户端配置表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `redis_prefix`;
CREATE TABLE `redis_prefix` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Redis Key模板表主键id',
    `service_name` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务名称(对应spring.application.name)',
    `key_code` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板编码(唯一,如: AUTH_REFRESH)',
    `key_template` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '完整Key模板(例: auth:refresh:{refreshToken})',
    `version` BIGINT NOT NULL DEFAULT 1 COMMENT '模板版本号(每次变更+1,用于增量刷新)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0：禁用，1：启用)',
    `remark` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '备注',
    `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_service_key_code` (`service_name` ASC, `key_code` ASC) USING BTREE,
    INDEX `idx_service_status_version` (`service_name` ASC, `status` ASC, `version` ASC) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '服务Redis键前缀配置表' ROW_FORMAT = Dynamic;


-- 权限中心表（认证中心统一鉴权/权限快照）

-- 角色表
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色表主键id',
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

-- 资源表（标识所有接口/菜单/数据实体）
DROP TABLE IF EXISTS `resources`;
CREATE TABLE `resources` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '资源表主键id',
    `name` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源名称(唯一)',
    `code` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源标识(唯一,支持通配符*，如：user:info:* | user:*:read | *)',
    `type` VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型( MENU | API | DATA | COMPONENT )',
    `parent_id` BIGINT NULL DEFAULT NULL COMMENT '父资源ID(用于递归表示资源层级)',
    `path` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '接口路径/路由(如果类型为 MENU | API | DATA )',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '资源状态(0：禁用，1：启用)',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_name_code` (`name` ASC, `code` ASC) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资源表' ROW_FORMAT = Dynamic;

-- 资源字段表（实现字段级权限/元素级显隐时可复用）
DROP TABLE IF EXISTS `resource_fields`;
CREATE TABLE `resource_fields` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '资源字段表主键id',
    `resource_id` BIGINT NOT NULL COMMENT '资源表外键id(关联resources表的id字段)',
    `field_name` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段名称(唯一，如：nickname | avatar_uuid | gender | birthday)',
    `description` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '字段描述',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '资源字段状态(0：禁用，1：启用)',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_resource_id` (`resource_id` ASC) USING BTREE,
    FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资源字段表' ROW_FORMAT = Dynamic;

-- 用户角色关联表（不对User库users做外键，避免跨库依赖）
DROP TABLE IF EXISTS `related_users_roles`;
CREATE TABLE `related_users_roles` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户角色关联表主键id',
    `user_id` BIGINT NOT NULL COMMENT '用户ID(来自User服务users.id)',
    `role_id` BIGINT NOT NULL COMMENT '角色表外键id(关联roles表的id字段)',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '关联状态(0：禁用，1：启用)',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_user_role` (`user_id` ASC, `role_id` ASC) USING BTREE,
    INDEX `idx_user_id` (`user_id` ASC) USING BTREE,
    INDEX `idx_role_id` (`role_id` ASC) USING BTREE,
    FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关联表' ROW_FORMAT = Dynamic;

-- 数据权限规则表（行级权限）
DROP TABLE IF EXISTS `data_rules`;
CREATE TABLE `data_rules` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据权限规则表主键id',
    `name` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称(唯一)',
    `code` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则编码( ALL | SELF | ROLE | CUSTOM )',
    `sql` VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '自定义规则SQL表达式',
    `description` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '规则描述',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '启用状态(0：禁用，1：启用)',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '规则创建时间戳',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '规则更新时间戳',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_name` (`name` ASC) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '数据权限规则表' ROW_FORMAT = Dynamic;

-- 角色权限关联表（角色+资源+字段+行级规则）
DROP TABLE IF EXISTS `related_roles_resources_fields_rules`;
CREATE TABLE `related_roles_resources_fields_rules` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色权限关联表主键id',
    `role_id` BIGINT NOT NULL COMMENT '角色表外键id(关联roles表的id字段)',
    `resource_id` BIGINT NOT NULL COMMENT '资源表外键id(关联resources表的id字段)',
    `field_ids` VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '*' COMMENT '字段ID列表(多个字段ID用英文逗号分隔，*表示所有字段)',
    `rule_id` BIGINT NOT NULL COMMENT '规则表外键id(关联data_rules表的id字段)',
    `description` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '角色权限关联描述',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '关联状态(0：禁用，1：启用)',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_role_resource` (`role_id` ASC, `resource_id` ASC) USING BTREE,
    FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (`rule_id`) REFERENCES `data_rules` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色权限关联表' ROW_FORMAT = Dynamic;

-- 角色继承约束表
DROP TABLE IF EXISTS `related_roles_constraint`;
CREATE TABLE `related_roles_constraint` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色继承约束表主键id',
    `role_id` BIGINT NOT NULL COMMENT '角色表外键id(关联roles表的id字段)',
    `exclude_role_id` BIGINT NOT NULL COMMENT '互斥角色表外键id(关联roles表的id字段)',
    `type` VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'EXCLUDE' COMMENT '约束类型( EXCLUDE | INHERIT )',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '启用状态(0：禁用，1：启用)',
    PRIMARY KEY (`id`) USING BTREE,
    FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (`exclude_role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色继承约束表' ROW_FORMAT = Dynamic;

-- 默认数据：角色/规则/资源/字段/默认权限
INSERT INTO `roles` (`name`, `level`, `parent_role_id`, `description`, `status`) VALUES
    ('SUPER_ADMIN', 9, NULL, '超级管理员', 1),
    ('ADMIN', 6, NULL, '管理员', 1),
    ('USER', 1, NULL, '普通用户', 1);

INSERT INTO `data_rules` (`name`, `code`, `sql`, `description`, `status`) VALUES
    ('ALL', 'ALL', '1=1', '默认全量规则', 1);

INSERT INTO `resources` (`name`, `code`, `type`, `parent_id`, `path`, `status`) VALUES
    ('全通配符权限', '*', 'API', NULL, '*', 1),
    ('用户公开信息查询', 'user:public:read', 'API', NULL, '/user-info', 1),
    ('当前用户信息查询', 'user:self:read', 'API', NULL, '/me', 1),
    ('当前用户信息更新', 'user:self:update', 'API', NULL, '/user-info', 1),
    ('用户信息数据资源', 'data:user_info', 'DATA', NULL, 'user_info', 1);

INSERT INTO `resource_fields` (`resource_id`, `field_name`, `description`, `status`)
SELECT rs.id, 'nickname', '昵称', 1 FROM `resources` rs WHERE rs.`code`='data:user_info' LIMIT 1;
INSERT INTO `resource_fields` (`resource_id`, `field_name`, `description`, `status`)
SELECT rs.id, 'avatar_uuid', '头像文件UUID', 1 FROM `resources` rs WHERE rs.`code`='data:user_info' LIMIT 1;
INSERT INTO `resource_fields` (`resource_id`, `field_name`, `description`, `status`)
SELECT rs.id, 'gender', '性别', 1 FROM `resources` rs WHERE rs.`code`='data:user_info' LIMIT 1;
INSERT INTO `resource_fields` (`resource_id`, `field_name`, `description`, `status`)
SELECT rs.id, 'birthday', '生日(敏感字段)', 1 FROM `resources` rs WHERE rs.`code`='data:user_info' LIMIT 1;

-- SUPER_ADMIN 默认权限：授予全通配符(*)
INSERT INTO `related_roles_resources_fields_rules` (`role_id`, `resource_id`, `field_ids`, `rule_id`, `description`, `status`)
SELECT r.id, rs.id, '*', dr.id, 'SUPER_ADMIN全通配符权限', 1
FROM `roles` r, `resources` rs, `data_rules` dr
WHERE r.`name`='SUPER_ADMIN' AND rs.`code`='*' AND dr.`code`='ALL'
LIMIT 1;

-- USER 默认权限：公共信息read + 自己信息read/update
INSERT INTO `related_roles_resources_fields_rules` (`role_id`, `resource_id`, `field_ids`, `rule_id`, `description`, `status`)
SELECT r.id, rs.id, '*', dr.id, 'USER默认API权限', 1
FROM `roles` r, `resources` rs, `data_rules` dr
WHERE r.`name`='USER' AND rs.`code` IN ('user:public:read','user:self:read','user:self:update') AND dr.`code`='ALL';

-- USER 字段级默认权限：隐藏birthday
INSERT INTO `related_roles_resources_fields_rules` (`role_id`, `resource_id`, `field_ids`, `rule_id`, `description`, `status`)
SELECT r.id, rs.id,
       (SELECT GROUP_CONCAT(rf.id) FROM `resource_fields` rf WHERE rf.`resource_id`=rs.id AND rf.`field_name` IN ('nickname','avatar_uuid','gender')),
       dr.id, 'USER默认字段级权限(隐藏birthday)', 1
FROM `roles` r, `resources` rs, `data_rules` dr
WHERE r.`name`='USER' AND rs.`code`='data:user_info' AND dr.`code`='ALL'
LIMIT 1;

-- ADMIN 默认权限：与USER一致（后续扩展）
INSERT INTO `related_roles_resources_fields_rules` (`role_id`, `resource_id`, `field_ids`, `rule_id`, `description`, `status`)
SELECT r.id, rs.id, '*', dr.id, 'ADMIN默认权限', 1
FROM `roles` r, `resources` rs, `data_rules` dr
WHERE r.`name`='ADMIN' AND rs.`code` IN ('user:public:read','user:self:read','user:self:update') AND dr.`code`='ALL';

--
SET FOREIGN_KEY_CHECKS = 1;