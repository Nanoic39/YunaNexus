/*
 数据库名称：core_yunanexus_file
 数据库字符集：utf8mb4
 数据库排序规则：utf8mb4_unicode_ci
 */
-- 数据库基本设置
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 存储节点表
DROP TABLE IF EXISTS `file_storage_node`;
CREATE TABLE `file_storage_node` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储节点主键id',
    `node_code` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点编码(唯一)',
    `node_name` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点名称',
    `storage_vendor` TINYINT NOT NULL DEFAULT 0 COMMENT '存储厂商(0: 本地磁盘, 1: MinIO, 2: S3, 3: OSS, 4: COS)',
    `endpoint` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '访问端点地址',
    `bucket_name` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '桶/命名空间',
    `region` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '区域',
    `weight` INT NOT NULL DEFAULT 100 COMMENT '节点权重(用于写入负载均衡)',
    `health_status` TINYINT NOT NULL DEFAULT 1 COMMENT '健康状态(0: 不健康, 1: 健康)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0: 禁用, 1: 启用)',
    `last_heartbeat_time` DATETIME NULL COMMENT '最近心跳时间',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_node_code` (`node_code` ASC) USING BTREE,
    INDEX `idx_health_status` (`health_status`, `status`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件存储节点表' ROW_FORMAT = Dynamic;

-- 物理对象表（逻辑文件与物理对象分离，支持去重与秒传）
DROP TABLE IF EXISTS `file_object`;
CREATE TABLE `file_object` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理对象主键id',
    `object_uuid` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物理对象UUID',
    `object_key` VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物理对象键(存储系统内部路径)',
    `primary_node_id` BIGINT NULL COMMENT '主存储节点id',
    `storage_type` TINYINT NOT NULL DEFAULT 0 COMMENT '存储方式(0: 磁盘对象, 1: 数据库存储小文件)',
    `file_hash` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件哈希值',
    `hash_algo` VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SHA256' COMMENT '哈希算法',
    `file_size` BIGINT NOT NULL COMMENT '文件大小(字节)',
    `file_ext` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '扩展名',
    `file_mime` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MIME类型',
    `is_encrypted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否加密(0: 否, 1: 是)',
    `compression_type` TINYINT NOT NULL DEFAULT 0 COMMENT '压缩类型(0: 无, 1: gzip, 2: zstd)',
    `ref_count` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑引用计数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0: 禁用, 1: 正常, 2: 待清理)',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_object_uuid` (`object_uuid` ASC) USING BTREE,
    UNIQUE INDEX `ui_object_key` (`object_key` ASC) USING BTREE,
    UNIQUE INDEX `ui_hash_size_algo` (`file_hash` ASC, `file_size` ASC, `hash_algo` ASC) USING BTREE,
    INDEX `idx_primary_node_status` (`primary_node_id`, `status`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物理文件对象表' ROW_FORMAT = Dynamic;

-- 用户目录表（用于实现虚拟目录）
DROP TABLE IF EXISTS `user_folder`;
CREATE TABLE `user_folder` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户目录主键id',
    `folder_uuid` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目录UUID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户id',
    `parent_id` BIGINT NULL COMMENT '父目录id(根目录为NULL)',
    `folder_name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目录名称',
    `folder_type` TINYINT NOT NULL DEFAULT 1 COMMENT '目录类型(0: 用户公开目录, 1: 用户主站目录, 2: OAuthApp目录)',
    `service_name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'main-site' COMMENT '所属服务名(主站/第三方应用)',
    `oauth_app_uuid` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'OAuth应用uuid(仅OAuthApp目录)',
    `folder_path` VARCHAR(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '/' COMMENT '目录全路径(用于快速查询)',
    `folder_path_hash` BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(`folder_path`, 256))) STORED COMMENT '目录路径哈希(用于唯一约束)',
    `depth` INT NOT NULL DEFAULT 0 COMMENT '目录深度',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '排序值',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0: 禁用, 1: 启用)',
    `delete_stage` TINYINT NOT NULL DEFAULT 0 COMMENT '删除阶段(0: 正常, 1: 回收站, 2: 预删除)',
    `deleted_at` DATETIME NULL COMMENT '进入删除流程时间',
    `recycle_expire_at` DATETIME NULL COMMENT '回收站到期时间',
    `pre_delete_expire_at` DATETIME NULL COMMENT '预删除到期时间',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_folder_uuid` (`folder_uuid` ASC) USING BTREE,
    UNIQUE INDEX `ui_user_parent_name_service` (`user_id` ASC, `parent_id` ASC, `folder_name` ASC, `service_name` ASC) USING BTREE,
    UNIQUE INDEX `ui_user_folder_path_stage` (`user_id` ASC, `service_name` ASC, `folder_path_hash` ASC, `delete_stage` ASC) USING BTREE,
    INDEX `idx_parent_id` (`parent_id`) USING BTREE,
    INDEX `idx_user_service_status` (`user_id`, `service_name`, `status`) USING BTREE,
    INDEX `idx_delete_stage_expire` (`delete_stage`, `recycle_expire_at`, `pre_delete_expire_at`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户目录表' ROW_FORMAT = Dynamic;

-- 用户逻辑文件表（记录用户可见文件，实际使用时指向物理表中id）
DROP TABLE IF EXISTS `user_file`;
CREATE TABLE `user_file` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户文件主键id',
    `file_uuid` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件UUID(外显)',
    `user_id` BIGINT NOT NULL COMMENT '所属用户id',
    `folder_id` BIGINT NULL COMMENT '所属目录id(NULL视为根目录)',
    `folder_id_safe` BIGINT GENERATED ALWAYS AS (IFNULL(`folder_id`, 0)) STORED COMMENT '根目录唯一约束辅助列',
    `object_id` BIGINT NOT NULL COMMENT '关联物理对象id',
    `file_category` TINYINT NOT NULL DEFAULT 1 COMMENT '文件分类(0: 用户公开文件, 1: 用户主站文件, 2: 用户OAuthApp文件)',
    `public_status` TINYINT NOT NULL DEFAULT 0 COMMENT '公开状态(0: 私有, 1: 公开)',
    `service_name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'main-site' COMMENT '所属服务名',
    `oauth_app_uuid` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '来源OAuth应用uuid',
    `origin_name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
    `file_name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '显示文件名',
    `file_size` BIGINT NOT NULL COMMENT '文件大小(字节)',
    `file_ext` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '扩展名',
    `file_mime` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MIME类型',
    `file_hash` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '文件哈希(便于业务层快速比对)',
    `version_no` INT NOT NULL DEFAULT 1 COMMENT '逻辑版本号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0: 禁用, 1: 正常)',
    `delete_stage` TINYINT NOT NULL DEFAULT 0 COMMENT '删除阶段(0: 正常, 1: 回收站, 2: 预删除)',
    `deleted_at` DATETIME NULL COMMENT '进入删除流程时间',
    `recycle_expire_at` DATETIME NULL COMMENT '回收站到期时间(默认+30天)',
    `pre_delete_expire_at` DATETIME NULL COMMENT '预删除到期时间(默认+60天)',
    `deleted_by` BIGINT NULL COMMENT '触发删除流程的用户id',
    `purged_by` BIGINT NULL COMMENT '最终物理删除执行者id(管理员手动删除时记录)',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_file_uuid` (`file_uuid` ASC) USING BTREE,
    UNIQUE INDEX `ui_user_folder_name_stage_service` (`user_id` ASC, `folder_id_safe` ASC, `file_name` ASC, `delete_stage` ASC, `service_name` ASC) USING BTREE,
    INDEX `idx_user_category_status` (`user_id`, `file_category`, `status`) USING BTREE,
    INDEX `idx_user_service_folder` (`user_id`, `service_name`, `folder_id`) USING BTREE,
    INDEX `idx_folder_id` (`folder_id`) USING BTREE,
    INDEX `idx_public_status` (`public_status`, `status`) USING BTREE,
    INDEX `idx_delete_stage_expire` (`delete_stage`, `recycle_expire_at`, `pre_delete_expire_at`) USING BTREE,
    INDEX `idx_object_id` (`object_id`) USING BTREE,
    INDEX `idx_oauth_app_uuid` (`oauth_app_uuid`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户文件逻辑表' ROW_FORMAT = Dynamic;

-- 分片上传任务表
DROP TABLE IF EXISTS `file_upload_task`;
CREATE TABLE `file_upload_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `upload_id` VARCHAR(64) NOT NULL,
    `user_id` BIGINT NOT NULL,
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
    `status` TINYINT NOT NULL DEFAULT 0,
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `ui_upload_id` (`upload_id`),
    INDEX `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
