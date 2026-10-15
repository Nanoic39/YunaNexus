/*
 数据库名称：yunanexus_file_0 / yunanexus_file_1 ...（分库）
 文件中心对应数据库；逻辑分片 0~15 按 file_id % 逻辑分片数 得到 shard_index，
 再按 shard_index % 物理库数 映射到物理库。
 新增物理库时：追加 CREATE DATABASE 与其下的 file_meta 建表。
 数据库字符集：utf8mb4
 数据库排序规则：utf8mb4_unicode_ci
 */
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============ 物理库 0 ============
CREATE DATABASE IF NOT EXISTS `yunanexus_file_0` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `yunanexus_file_0`;

-- 文件元数据表
CREATE TABLE IF NOT EXISTS `file_meta` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `file_id` bigint NOT NULL COMMENT '全局文件ID(meta分配,不包含分库信息)',
    `shard_index` int(11) NOT NULL COMMENT '逻辑分片编号(0~15)，上传时由文件中心决策',
    `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
    `ext` varchar(32) DEFAULT NULL COMMENT '扩展名(不含点,小写)',
    `content_type` varchar(128) DEFAULT NULL COMMENT 'MIME 类型',
    `size` bigint NOT NULL COMMENT '文件字节数',
    `storage_type` tinyint(4) NOT NULL DEFAULT '1' COMMENT '存储类型：1-本地 2-MinIO 3-OSS',
    `storage_path` varchar(512) NOT NULL COMMENT '存储路径/对象键',
    `sha256` varchar(64) DEFAULT NULL COMMENT '文件内容 SHA-256(去重与校验)',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-正常 0-已删除',
    `create_by` bigint DEFAULT NULL COMMENT '上传者用户ID(数据权限试点字段)',
    `dept_id` bigint DEFAULT NULL COMMENT '所属部门ID(数据权限试点字段)',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_id` (`file_id`),
    KEY `idx_sha256_size_status` (`sha256`, `size`, `status`),
    KEY `idx_storage_path` (`storage_path`),
    KEY `idx_create_by` (`create_by`),
    KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件元数据表';

-- ============ 物理库 1 ============
CREATE DATABASE IF NOT EXISTS `yunanexus_file_1` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `yunanexus_file_1`;

-- 文件元数据表
CREATE TABLE IF NOT EXISTS `file_meta` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `file_id` bigint NOT NULL COMMENT '全局文件ID(meta分配,不包含分库信息)',
    `shard_index` int(11) NOT NULL COMMENT '逻辑分片编号(0~15)，上传时由文件中心决策',
    `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
    `ext` varchar(32) DEFAULT NULL COMMENT '扩展名(不含点,小写)',
    `content_type` varchar(128) DEFAULT NULL COMMENT 'MIME 类型',
    `size` bigint NOT NULL COMMENT '文件字节数',
    `storage_type` tinyint(4) NOT NULL DEFAULT '1' COMMENT '存储类型：1-本地 2-MinIO 3-OSS',
    `storage_path` varchar(512) NOT NULL COMMENT '存储路径/对象键',
    `sha256` varchar(64) DEFAULT NULL COMMENT '文件内容 SHA-256(去重与校验)',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-正常 0-已删除',
    `create_by` bigint DEFAULT NULL COMMENT '上传者用户ID(数据权限试点字段)',
    `dept_id` bigint DEFAULT NULL COMMENT '所属部门ID(数据权限试点字段)',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_id` (`file_id`),
    KEY `idx_sha256_size_status` (`sha256`, `size`, `status`),
    KEY `idx_storage_path` (`storage_path`),
    KEY `idx_create_by` (`create_by`),
    KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件元数据表';

SET FOREIGN_KEY_CHECKS = 1;

-- 个人工作空间（2026-10-15）
USE yunanexus_file_0;
CREATE TABLE IF NOT EXISTS file_share (
 share_id CHAR(43) PRIMARY KEY, user_id BIGINT NOT NULL, file_id BIGINT NOT NULL,
 file_name VARCHAR(255) NOT NULL, code_hash CHAR(64) NULL,
 created_at DATETIME NOT NULL, expires_at DATETIME NOT NULL, revoked TINYINT NOT NULL DEFAULT 0,
 failed_attempts INT NOT NULL DEFAULT 0, failure_window DATETIME NULL,
 INDEX idx_owner(user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
