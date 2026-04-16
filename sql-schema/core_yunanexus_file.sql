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
    `storage_vendor` TINYINT NOT NULL DEFAULT 1 COMMENT '存储厂商(0: 本地磁盘, 1: MinIO, 2: S3, 3: OSS, 4: COS)',
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
    INDEX `idx_primary_node_status` (`primary_node_id`, `status`) USING BTREE,
    CONSTRAINT `fk_file_object_primary_node`
        FOREIGN KEY (`primary_node_id`) REFERENCES `file_storage_node` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物理文件对象表' ROW_FORMAT = Dynamic;

-- 物理副本表（提高高可用与容灾能力）
DROP TABLE IF EXISTS `file_object_replica`;
CREATE TABLE `file_object_replica` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理副本主键id',
    `object_id` BIGINT NOT NULL COMMENT '物理对象id',
    `node_id` BIGINT NOT NULL COMMENT '副本节点id',
    `replica_key` VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '副本对象键',
    `is_primary` TINYINT NOT NULL DEFAULT 0 COMMENT '是否主副本(0: 否, 1: 是)',
    `sync_version` BIGINT NOT NULL DEFAULT 1 COMMENT '同步版本号',
    `last_sync_time` DATETIME NULL COMMENT '最后同步时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0: 异常, 1: 正常)',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_object_node` (`object_id` ASC, `node_id` ASC) USING BTREE,
    INDEX `idx_node_status` (`node_id`, `status`) USING BTREE,
    CONSTRAINT `fk_replica_object`
        FOREIGN KEY (`object_id`) REFERENCES `file_object` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_replica_node`
        FOREIGN KEY (`node_id`) REFERENCES `file_storage_node` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物理文件副本表' ROW_FORMAT = Dynamic;

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
    UNIQUE INDEX `ui_user_folder_path_stage` (`user_id` ASC, `folder_path` ASC, `delete_stage` ASC) USING BTREE,
    INDEX `idx_user_service_status` (`user_id`, `service_name`, `status`) USING BTREE,
    INDEX `idx_delete_stage_expire` (`delete_stage`, `recycle_expire_at`, `pre_delete_expire_at`) USING BTREE,
    CONSTRAINT `fk_user_folder_parent`
        FOREIGN KEY (`parent_id`) REFERENCES `user_folder` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户目录表' ROW_FORMAT = Dynamic;

-- 用户逻辑文件表（记录用户可见文件，实际使用时指向物理表中id）
DROP TABLE IF EXISTS `user_file`;
CREATE TABLE `user_file` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户文件主键id',
    `file_uuid` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件UUID(外显)',
    `user_id` BIGINT NOT NULL COMMENT '所属用户id',
    `folder_id` BIGINT NULL COMMENT '所属目录id(NULL视为根目录)',
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
    UNIQUE INDEX `ui_user_folder_name_stage_service` (`user_id` ASC, `folder_id` ASC, `file_name` ASC, `delete_stage` ASC, `service_name` ASC) USING BTREE,
    INDEX `idx_user_category_status` (`user_id`, `file_category`, `status`) USING BTREE,
    INDEX `idx_user_service_folder` (`user_id`, `service_name`, `folder_id`) USING BTREE,
    INDEX `idx_public_status` (`public_status`, `status`) USING BTREE,
    INDEX `idx_delete_stage_expire` (`delete_stage`, `recycle_expire_at`, `pre_delete_expire_at`) USING BTREE,
    INDEX `idx_object_id` (`object_id`) USING BTREE,
    INDEX `idx_oauth_app_uuid` (`oauth_app_uuid`) USING BTREE,
    CONSTRAINT `fk_user_file_folder`
        FOREIGN KEY (`folder_id`) REFERENCES `user_folder` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `fk_user_file_object`
        FOREIGN KEY (`object_id`) REFERENCES `file_object` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户文件逻辑表' ROW_FORMAT = Dynamic;

-- 文件删除任务表（用于异步推进回收站与预删除流程）
DROP TABLE IF EXISTS `user_file_delete_task`;
CREATE TABLE `user_file_delete_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '删除任务主键id',
    `task_uuid` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务UUID',
    `file_id` BIGINT NOT NULL COMMENT '关联用户文件id',
    `current_stage` TINYINT NOT NULL DEFAULT 1 COMMENT '当前阶段(1: 回收站, 2: 预删除)',
    `target_stage` TINYINT NOT NULL DEFAULT 2 COMMENT '目标阶段(2: 预删除, 3: 物理删除)',
    `execute_at` DATETIME NOT NULL COMMENT '计划执行时间',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    `max_retry_count` INT NOT NULL DEFAULT 5 COMMENT '最大重试次数',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '任务状态(0: 待执行, 1: 执行中, 2: 成功, 3: 失败, 4: 取消)',
    `operator_id` BIGINT NULL COMMENT '触发者用户id',
    `last_error` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '最后一次错误信息',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_task_uuid` (`task_uuid` ASC) USING BTREE,
    INDEX `idx_status_execute` (`status`, `execute_at`) USING BTREE,
    INDEX `idx_file_status` (`file_id`, `status`) USING BTREE,
    CONSTRAINT `fk_delete_task_file`
        FOREIGN KEY (`file_id`) REFERENCES `user_file` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件删除任务表' ROW_FORMAT = Dynamic;

-- 文件分享表（支持密码/次数限制/过期控制）
DROP TABLE IF EXISTS `user_file_share`;
CREATE TABLE `user_file_share` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件分享表主键id',
    `share_uuid` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分享UUID',
    `share_code` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '短分享码(可用于短链接)',
    `file_id` BIGINT NOT NULL COMMENT '关联文件id',
    `file_uuid` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联文件uuid(冗余字段，减少联表)',
    `owner_user_id` BIGINT NOT NULL COMMENT '分享发起者用户id',
    `share_password` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '分享密码(建议存密文)',
    `need_login` TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要登录访问(0: 否, 1: 是)',
    `allow_preview` TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许预览(0: 否, 1: 是)',
    `allow_download` TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许下载(0: 否, 1: 是)',
    `max_view_count` INT NULL COMMENT '最大查看次数(NULL为不限制)',
    `max_download_count` INT NULL COMMENT '最大下载次数(NULL为不限制)',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '已查看次数',
    `download_count` INT NOT NULL DEFAULT 0 COMMENT '已下载次数',
    `expire_time` DATETIME NULL COMMENT '分享过期时间(NULL为不过期)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0: 失效, 1: 生效, 2: 已过期)',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `ui_share_uuid` (`share_uuid` ASC) USING BTREE,
    UNIQUE INDEX `ui_share_code` (`share_code` ASC) USING BTREE,
    INDEX `idx_file_status` (`file_id`, `status`) USING BTREE,
    INDEX `idx_owner_status` (`owner_user_id`, `status`) USING BTREE,
    INDEX `idx_expire_status` (`expire_time`, `status`) USING BTREE,
    CONSTRAINT `fk_share_file`
        FOREIGN KEY (`file_id`) REFERENCES `user_file` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件分享表' ROW_FORMAT = Dynamic;

-- 分享访问日志（用于审计、风控、热点分析）
DROP TABLE IF EXISTS `user_file_share_access_log`;
CREATE TABLE `user_file_share_access_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分享访问日志主键id',
    `share_id` BIGINT NOT NULL COMMENT '关联分享id',
    `file_id` BIGINT NOT NULL COMMENT '关联文件id',
    `access_type` TINYINT NOT NULL COMMENT '访问类型(0: 预览, 1: 下载)',
    `access_result` TINYINT NOT NULL DEFAULT 1 COMMENT '访问结果(0: 失败, 1: 成功)',
    `failure_reason` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '失败原因',
    `visitor_user_id` BIGINT NULL COMMENT '访问者用户id(未登录为NULL)',
    `visitor_ip` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '访问IP',
    `user_agent` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'UA',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_share_time` (`share_id`, `create_time`) USING BTREE,
    INDEX `idx_file_time` (`file_id`, `create_time`) USING BTREE,
    INDEX `idx_visitor_time` (`visitor_user_id`, `create_time`) USING BTREE,
    CONSTRAINT `fk_share_access_log_share`
        FOREIGN KEY (`share_id`) REFERENCES `user_file_share` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_share_access_log_file`
        FOREIGN KEY (`file_id`) REFERENCES `user_file` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件分享访问日志表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
