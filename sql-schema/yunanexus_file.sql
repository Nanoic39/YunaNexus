/*
 数据库名称：yunanexus_file
 数据库字符集：utf8mb4
 数据库排序规则：utf8mb4_unicode_ci
 */
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 存储节点表
DROP TABLE IF EXISTS `file_storage_node`;
CREATE TABLE `file_storage_node` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储节点主键id',
    `node_code` VARCHAR(64) NOT NULL COMMENT '节点编码(唯一)',
    `node_name` VARCHAR(128) NOT NULL COMMENT '节点名称',
    `storage_vendor` TINYINT NOT NULL DEFAULT 0 COMMENT '存储厂商(0:本地, 1:MinIO, 2:S3, 3:OSS, 4:COS)',
    `endpoint` VARCHAR(255) NOT NULL COMMENT '访问端点',
    `bucket_name` VARCHAR(128) NOT NULL COMMENT '桶/命名空间',
    `region` VARCHAR(64) NULL COMMENT '区域',
    `weight` INT NOT NULL DEFAULT 100 COMMENT '写入负载权重',
    `health_status` TINYINT NOT NULL DEFAULT 1 COMMENT '0:不健康, 1:健康',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0:禁用, 1:启用',
    `last_heartbeat_time` DATETIME NULL COMMENT '最近心跳时间',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_node_code` (`node_code`) USING BTREE,
    INDEX `idx_health_status` (`health_status`, `status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件存储节点表' ROW_FORMAT=Dynamic;

-- 物理对象表（去重与秒传）
DROP TABLE IF EXISTS `file_object`;
CREATE TABLE `file_object` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `object_uuid` VARCHAR(36) NOT NULL COMMENT '物理对象UUID',
    `object_key` VARCHAR(512) NOT NULL COMMENT '存储系统内部路径',
    `primary_node_id` BIGINT NULL COMMENT '主存储节点id',
    `storage_type` TINYINT NOT NULL DEFAULT 0 COMMENT '0:磁盘, 1:数据库小文件',
    `file_hash` VARCHAR(128) NOT NULL COMMENT '文件哈希值',
    `hash_algo` VARCHAR(16) NOT NULL DEFAULT 'SHA256' COMMENT '哈希算法',
    `file_size` BIGINT NOT NULL COMMENT '文件大小(字节)',
    `file_ext` VARCHAR(50) NOT NULL COMMENT '扩展名',
    `file_mime` VARCHAR(100) NOT NULL COMMENT 'MIME类型',
    `preview_object_id` BIGINT NULL COMMENT '关联预览/缩略图物理对象id',
    `is_encrypted` TINYINT NOT NULL DEFAULT 0 COMMENT '0:否, 1:是',
    `ref_count` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑引用计数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0:禁用, 1:正常, 2:待清理',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_object_uuid` (`object_uuid`) USING BTREE,
    UNIQUE INDEX `ui_object_key` (`object_key`) USING BTREE,
    UNIQUE INDEX `ui_hash_size_algo` (`file_hash`, `file_size`, `hash_algo`) USING BTREE,
    INDEX `idx_primary_node_status` (`primary_node_id`, `status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物理文件对象表' ROW_FORMAT=Dynamic;

-- 用户目录表
DROP TABLE IF EXISTS `user_folder`;
CREATE TABLE `user_folder` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `folder_uuid` VARCHAR(36) NOT NULL COMMENT '目录UUID',
    `global_id` BINARY(6) NOT NULL COMMENT '所属用户global_id',
    `parent_id` BIGINT NULL COMMENT '父目录id',
    `folder_name` VARCHAR(255) NOT NULL COMMENT '目录名称',
    `folder_type` TINYINT NOT NULL DEFAULT 1 COMMENT '0:公开, 1:主站, 2:OAuthApp',
    `service_name` VARCHAR(100) NOT NULL DEFAULT 'main-site' COMMENT '所属服务名',
    `oauth_app_uuid` VARCHAR(36) NULL COMMENT 'OAuth应用uuid',
    `folder_path` VARCHAR(1024) NOT NULL DEFAULT '/' COMMENT '目录全路径',
    `depth` INT NOT NULL DEFAULT 0 COMMENT '目录深度',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '排序值',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0:禁用, 1:启用',
    `delete_stage` TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常, 1:回收站, 2:预删除',
    `deleted_at` DATETIME NULL,
    `recycle_expire_at` DATETIME NULL,
    `pre_delete_expire_at` DATETIME NULL,
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_folder_uuid` (`folder_uuid`) USING BTREE,
    UNIQUE INDEX `ui_user_parent_name_service` (`global_id`, `parent_id`, `folder_name`, `service_name`) USING BTREE,
    INDEX `idx_parent_id` (`parent_id`) USING BTREE,
    INDEX `idx_global_service_status` (`global_id`, `service_name`, `status`) USING BTREE,
    INDEX `idx_delete_stage_expire` (`delete_stage`, `recycle_expire_at`, `pre_delete_expire_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户目录表' ROW_FORMAT=Dynamic;

-- 用户逻辑文件表
DROP TABLE IF EXISTS `user_file`;
CREATE TABLE `user_file` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `file_uuid` VARCHAR(36) NOT NULL COMMENT '文件UUID(外显)',
    `global_id` BINARY(6) NOT NULL COMMENT '所属用户global_id',
    `folder_id` BIGINT NULL COMMENT '所属目录id',
    `object_id` BIGINT NOT NULL COMMENT '关联物理对象id',
    `file_category` TINYINT NOT NULL DEFAULT 1 COMMENT '0:公开, 1:主站, 2:OAuthApp, 3:系统资源(头像等)',
    `public_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0:私有, 1:公开',
    `service_name` VARCHAR(100) NOT NULL DEFAULT 'main-site',
    `oauth_app_uuid` VARCHAR(36) NULL,
    `origin_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_name` VARCHAR(255) NOT NULL COMMENT '显示文件名',
    `file_size` BIGINT NOT NULL COMMENT '文件大小(字节)',
    `file_ext` VARCHAR(50) NOT NULL COMMENT '扩展名',
    `file_mime` VARCHAR(100) NOT NULL COMMENT 'MIME类型',
    `file_hash` VARCHAR(128) NULL COMMENT '文件哈希',
    `version_no` INT NOT NULL DEFAULT 1,
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0:禁用, 1:正常',
    `delete_stage` TINYINT NOT NULL DEFAULT 0 COMMENT '0:正常, 1:回收站, 2:预删除',
    `deleted_at` DATETIME NULL,
    `recycle_expire_at` DATETIME NULL COMMENT '回收站到期(默认+30天)',
    `pre_delete_expire_at` DATETIME NULL COMMENT '预删除到期(默认+60天)',
    `deleted_by` BINARY(6) NULL COMMENT '触发删除的用户global_id',
    `purged_by` BINARY(6) NULL COMMENT '物理删除执行者global_id',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_file_uuid` (`file_uuid`) USING BTREE,
    UNIQUE INDEX `ui_user_folder_name_stage_service` (`global_id`, `folder_id`, `file_name`, `delete_stage`, `service_name`) USING BTREE,
    INDEX `idx_global_category_status` (`global_id`, `file_category`, `status`) USING BTREE,
    INDEX `idx_global_service_folder` (`global_id`, `service_name`, `folder_id`) USING BTREE,
    INDEX `idx_folder_id` (`folder_id`) USING BTREE,
    INDEX `idx_public_status` (`public_status`, `status`) USING BTREE,
    INDEX `idx_delete_stage_expire` (`delete_stage`, `recycle_expire_at`, `pre_delete_expire_at`) USING BTREE,
    INDEX `idx_object_id` (`object_id`) USING BTREE,
    INDEX `idx_oauth_app_uuid` (`oauth_app_uuid`) USING BTREE,
    INDEX `idx_file_category` (`file_category`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户文件逻辑表' ROW_FORMAT=Dynamic;

-- 分片上传任务表
DROP TABLE IF EXISTS `file_upload_task`;
CREATE TABLE `file_upload_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `upload_id` VARCHAR(64) NOT NULL,
    `global_id` BINARY(6) NOT NULL,
    `folder_id` BIGINT NULL,
    `file_name` VARCHAR(255) NOT NULL,
    `file_size` BIGINT NOT NULL,
    `file_ext` VARCHAR(50) NULL,
    `file_mime` VARCHAR(100) NULL,
    `chunk_size` BIGINT NOT NULL,
    `total_chunks` INT NOT NULL,
    `uploaded_chunks` INT NOT NULL DEFAULT 0,
    `file_category` TINYINT NOT NULL DEFAULT 1,
    `public_status` TINYINT NOT NULL DEFAULT 0,
    `service_name` VARCHAR(100) NOT NULL DEFAULT 'main-site',
    `oauth_app_uuid` VARCHAR(36) NULL,
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0:上传中, 1:已完成, 2:已取消',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `ui_upload_id` (`upload_id`),
    INDEX `idx_global_status` (`global_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分片上传任务表';

-- 分片记录表
DROP TABLE IF EXISTS `file_upload_chunk`;
CREATE TABLE `file_upload_chunk` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `upload_id` VARCHAR(64) NOT NULL,
    `chunk_index` INT NOT NULL,
    `chunk_size` BIGINT NOT NULL,
    `etag` VARCHAR(128) NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `ui_upload_chunk` (`upload_id`, `chunk_index`),
    INDEX `idx_upload_id` (`upload_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分片记录表';

-- 文件分享表
DROP TABLE IF EXISTS `file_share`;
CREATE TABLE `file_share` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `share_uuid` VARCHAR(36) NOT NULL COMMENT '分享UUID',
    `share_code` VARCHAR(36) NOT NULL COMMENT '分享码(UUID格式)',
    `global_id` BINARY(6) NOT NULL COMMENT '发起分享的用户global_id',
    `extract_code` VARCHAR(10) NULL COMMENT '提取码(NULL=无密码)',
    `need_login` TINYINT NOT NULL DEFAULT 1 COMMENT '0:免登录, 1:需登录',
    `allow_preview` TINYINT NOT NULL DEFAULT 0 COMMENT '0:禁止, 1:允许预览',
    `max_view_count` BIGINT NOT NULL DEFAULT 0 COMMENT '最大访问次数(0=不限,到达后自动取消)',
    `max_download_count` BIGINT NOT NULL DEFAULT 0 COMMENT '最大下载次数(0=不限,到达后自动取消)',
    `view_count` BIGINT NOT NULL DEFAULT 0,
    `download_count` BIGINT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0:已取消, 1:有效, 2:已过期(自动)',
    `expire_at` DATETIME NULL COMMENT '过期时间(NULL=永久)',
    `last_viewed_at` DATETIME NULL,
    `last_downloaded_at` DATETIME NULL,
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_share_uuid` (`share_uuid`) USING BTREE,
    UNIQUE INDEX `ui_share_code` (`share_code`) USING BTREE,
    INDEX `idx_global_id` (`global_id`) USING BTREE,
    INDEX `idx_status_expire` (`status`, `expire_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件分享表' ROW_FORMAT=Dynamic;

-- 分享目标表（支持多文件/多目录）
DROP TABLE IF EXISTS `file_share_target`;
CREATE TABLE `file_share_target` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `share_id` BIGINT NOT NULL COMMENT '关联file_share.id',
    `target_type` TINYINT NOT NULL COMMENT '0:文件, 1:目录',
    `target_uuid` VARCHAR(36) NOT NULL COMMENT '文件/目录UUID',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '排序值',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_share_id` (`share_id`),
    INDEX `idx_target_uuid` (`target_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分享目标表';

-- 文件存储配额表
DROP TABLE IF EXISTS `file_storage_quota`;
CREATE TABLE `file_storage_quota` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `role_name` VARCHAR(32) NOT NULL COMMENT '角色名',
    `max_single_file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '单文件最大字节(0=不限)',
    `max_total_storage` BIGINT NOT NULL DEFAULT 0 COMMENT '总空间最大字节(0=不限)',
    `priority` INT NOT NULL DEFAULT 0 COMMENT '优先级(越大越优先)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0:禁用, 1:启用',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `ui_role_name` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件存储配额表';

INSERT INTO `file_storage_quota` (`role_name`, `max_single_file_size`, `max_total_storage`, `priority`) VALUES
('SUPER_ADMIN', 0, 0, 99),          /* 无限 */
('ADMIN', 0, 0, 60),                /* 无限 */
('VIP', 107374182400, 429496729600, 10),  /* 单文件100GB, 总量400GB */
('USER', 53687091200, 214748364800, 1);   /* 单文件50GB, 总量200GB */

SET FOREIGN_KEY_CHECKS = 1;