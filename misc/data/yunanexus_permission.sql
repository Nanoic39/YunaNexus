/*
 数据库名称：yunanexus_permission
 权限中心对应数据库（不分库）
 数据库字符集：utf8mb4
 数据库排序规则：utf8mb4_unicode_ci
 */
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `yunanexus_permission` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `yunanexus_permission`;

-- 角色表
CREATE TABLE IF NOT EXISTS `perm_role` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `role_code` varchar(64) NOT NULL COMMENT '角色标识(唯一,如 admin/user)',
    `role_name` varchar(128) NOT NULL COMMENT '角色名称(展示用)',
    `description` varchar(255) DEFAULT NULL COMMENT '角色描述',
    `data_scope` tinyint(4) NOT NULL DEFAULT '4' COMMENT '数据范围：1-全部 2-本部门 3-本部门及以下 4-仅本人 5-自定义',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-启用 0-禁用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 权限表（资源 + 动作）
CREATE TABLE IF NOT EXISTS `perm_permission` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `perm_code` varchar(128) NOT NULL COMMENT '权限标识(唯一,如 file:upload)',
    `resource` varchar(255) NOT NULL COMMENT '资源标识(URL 或资源名)',
    `action` varchar(32) NOT NULL COMMENT '动作(read/write/delete/...)',
    `description` varchar(255) DEFAULT NULL COMMENT '权限描述',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-启用 0-禁用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`),
    KEY `idx_resource_action` (`resource`(191), `action`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- 用户-角色绑定表
CREATE TABLE IF NOT EXISTS `perm_user_role` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id` bigint NOT NULL COMMENT '全局用户ID',
    `role_id` bigint NOT NULL COMMENT '角色ID(perm_role.id)',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色绑定表';

-- 角色-权限绑定表
CREATE TABLE IF NOT EXISTS `perm_role_permission` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `role_id` bigint NOT NULL COMMENT '角色ID(perm_role.id)',
    `permission_id` bigint NOT NULL COMMENT '权限ID(perm_permission.id)',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限绑定表';

-- ABAC 策略表
CREATE TABLE IF NOT EXISTS `perm_policy` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `policy_name` varchar(128) NOT NULL COMMENT '策略名称',
    `resource` varchar(255) NOT NULL COMMENT '资源模式(支持 Ant 通配,如 /api/v1/file/**)',
    `action` varchar(32) NOT NULL COMMENT '动作(* 表示任意动作)',
    `effect` tinyint(4) NOT NULL COMMENT '效果：1-允许 0-拒绝',
    `conditions` varchar(2048) DEFAULT NULL COMMENT '属性条件(JSON 数组,元素 {attribute,operator,value})',
    `priority` int(11) NOT NULL DEFAULT '0' COMMENT '优先级(数值越大越先判定)',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-启用 0-禁用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_resource_action` (`resource`(191), `action`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ABAC 策略表';

-- 部门表
CREATE TABLE IF NOT EXISTS `perm_dept` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `dept_code` varchar(64) NOT NULL COMMENT '部门编码(唯一)',
    `dept_name` varchar(128) NOT NULL COMMENT '部门名称',
    `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父部门ID(0=根)',
    `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序(升序)',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-启用 0-禁用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dept_code` (`dept_code`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

-- 部门闭包表（祖先-后代全路径，含自身 depth=0）
CREATE TABLE IF NOT EXISTS `perm_dept_closure` (
    `ancestor_id` bigint NOT NULL COMMENT '祖先部门ID',
    `descendant_id` bigint NOT NULL COMMENT '后代部门ID',
    `depth` int(11) NOT NULL DEFAULT '0' COMMENT '层级距离(0=自身)',
    PRIMARY KEY (`ancestor_id`, `descendant_id`),
    KEY `idx_descendant` (`descendant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门闭包表';

-- 用户-部门绑定表（单部门）
CREATE TABLE IF NOT EXISTS `perm_user_dept` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id` bigint NOT NULL COMMENT '全局用户ID',
    `dept_id` bigint NOT NULL COMMENT '部门ID(perm_dept.id)',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-部门绑定表';

-- 角色-自定义数据范围部门表（data_scope=5 时生效）
CREATE TABLE IF NOT EXISTS `perm_role_dept` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `role_id` bigint NOT NULL COMMENT '角色ID(perm_role.id)',
    `dept_id` bigint NOT NULL COMMENT '部门ID(perm_dept.id)',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_dept` (`role_id`, `dept_id`),
    KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-自定义数据范围部门表';

-- 资源实例级授权表（L3：按具体对象授权，Phase B）
CREATE TABLE IF NOT EXISTS `perm_resource_grant` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `subject_type` tinyint(4) NOT NULL COMMENT '主体类型：1-用户 2-角色 3-部门',
    `subject_id` bigint NOT NULL COMMENT '主体ID(用户ID/角色ID/部门ID)',
    `resource_type` varchar(64) NOT NULL COMMENT '资源类型(如 file/project)',
    `resource_id` varchar(64) NOT NULL COMMENT '资源实例ID',
    `action` varchar(32) NOT NULL COMMENT '动作(read/write/delete/* 表示任意)',
    `effect` tinyint(4) NOT NULL DEFAULT '1' COMMENT '效果：1-允许 0-拒绝',
    `expire_time` datetime DEFAULT NULL COMMENT '过期时间(NULL=永久)',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-启用 0-禁用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_subject_resource_action` (`subject_type`, `subject_id`, `resource_type`, `resource_id`, `action`),
    KEY `idx_resource` (`resource_type`, `resource_id`),
    KEY `idx_subject` (`subject_type`, `subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源实例级授权表';

-- 字段级权限规则表（L5：字段/列级读写控制，Phase C）
CREATE TABLE IF NOT EXISTS `perm_field_rule` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `subject_type` tinyint(4) NOT NULL COMMENT '主体类型：1-用户 2-角色 3-部门',
    `subject_id` bigint NOT NULL COMMENT '主体ID(用户ID/角色ID/部门ID)',
    `resource_type` varchar(64) NOT NULL COMMENT '资源类型(如 user_profile)',
    `field_name` varchar(64) NOT NULL COMMENT '字段名(如 phone/email)',
    `access` tinyint(4) NOT NULL COMMENT '访问类型：1-读 2-写',
    `effect` tinyint(4) NOT NULL DEFAULT '1' COMMENT '效果：1-允许 0-拒绝',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-启用 0-禁用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_subject_field_access` (`subject_type`, `subject_id`, `resource_type`, `field_name`, `access`),
    KEY `idx_resource_access` (`resource_type`, `access`),
    KEY `idx_subject` (`subject_type`, `subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字段级权限规则表';

-- 菜单/页面资源表（L1：菜单与页面可见性，Phase D）
CREATE TABLE IF NOT EXISTS `perm_menu` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `menu_code` varchar(64) NOT NULL COMMENT '菜单编码(唯一)',
    `menu_name` varchar(128) NOT NULL COMMENT '菜单名称',
    `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父菜单ID(0=根)',
    `path` varchar(255) DEFAULT NULL COMMENT '前端路由路径',
    `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序(升序)',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-启用 0-禁用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_menu_code` (`menu_code`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单/页面资源表';

-- 角色-菜单绑定表
CREATE TABLE IF NOT EXISTS `perm_role_menu` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `role_id` bigint NOT NULL COMMENT '角色ID(perm_role.id)',
    `menu_id` bigint NOT NULL COMMENT '菜单ID(perm_menu.id)',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-菜单绑定表';
