/*
 数据库名称：yunanexus_notice
 通知中心对应数据库（单库，不分库）
 数据库字符集：utf8mb4
 数据库排序规则：utf8mb4_unicode_ci
 */
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `yunanexus_notice` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `yunanexus_notice`;

-- 通知模板表
CREATE TABLE IF NOT EXISTS `notice_template` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `template_code` varchar(64) NOT NULL COMMENT '模板编码(全局唯一)',
    `template_name` varchar(128) NOT NULL COMMENT '模板名称',
    `channel` tinyint(4) NOT NULL COMMENT '通道：1-站内信 2-邮件 3-短信',
    `title` varchar(255) DEFAULT NULL COMMENT '标题模板(支持 ${占位符})',
    `content` text COMMENT '内容模板(支持 ${占位符})',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-启用 0-停用',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_code` (`template_code`),
    KEY `idx_channel` (`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知模板表';

-- 通知记录表
CREATE TABLE IF NOT EXISTS `notice_record` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `notice_id` bigint NOT NULL COMMENT '全局通知ID(meta分配)',
    `template_code` varchar(64) NOT NULL COMMENT '模板编码',
    `channel` tinyint(4) NOT NULL COMMENT '通道：1-站内信 2-邮件 3-短信',
    `receiver` varchar(255) NOT NULL COMMENT '接收方：站内信为用户ID，邮件为邮箱，短信为手机号',
    `title` varchar(255) DEFAULT NULL COMMENT '渲染后标题',
    `content` text COMMENT '渲染后内容',
    `params` text COMMENT '模板参数(JSON)',
    `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态：0-待发送 1-已发送 2-发送失败 3-已读(站内信)',
    `retry_count` int(11) NOT NULL DEFAULT '0' COMMENT '重试次数',
    `send_time` datetime DEFAULT NULL COMMENT '发送成功时间',
    `error_msg` varchar(512) DEFAULT NULL COMMENT '失败原因',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_notice_id` (`notice_id`),
    KEY `idx_template_code` (`template_code`),
    KEY `idx_receiver` (`receiver`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知记录表';

-- 门户站点配置表（门户内容持久化，站点级元信息；与前端 SiteMeta 对齐）
CREATE TABLE IF NOT EXISTS `portal_site` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `site_key` varchar(64) NOT NULL COMMENT '站点标识(默认 default)',
    `name` varchar(128) NOT NULL COMMENT '站点名称',
    `tagline` varchar(255) DEFAULT NULL COMMENT '标语',
    `description` varchar(512) DEFAULT NULL COMMENT '简介',
    `version` varchar(32) DEFAULT NULL COMMENT '版本号',
    `repository_url` varchar(255) DEFAULT NULL COMMENT '代码仓库地址',
    `footer_note` varchar(255) DEFAULT NULL COMMENT '页脚文案',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-启用 0-停用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_site_key` (`site_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门户站点配置表';

-- 门户页面表（页面级内容；sections/elements/items 以 JSON 落库，与前端 ContentPage 对齐）
CREATE TABLE IF NOT EXISTS `portal_page` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `site_key` varchar(64) NOT NULL COMMENT '站点标识',
    `page_id` varchar(64) NOT NULL COMMENT '页面标识(站点内唯一，前端路由 key)',
    `path` varchar(255) NOT NULL COMMENT '路由路径(如 /、/about)',
    `title` varchar(255) NOT NULL COMMENT '页面标题',
    `description` varchar(512) DEFAULT NULL COMMENT '页面描述',
    `nav` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否在门户导航展示：1-是 0-否',
    `sections` longtext COMMENT '区块内容(JSON 数组：sections→elements→items)',
    `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '排序号(升序)',
    `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-已发布 0-草稿',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_site_page` (`site_key`, `page_id`),
    KEY `idx_site_path` (`site_key`, `path`),
    KEY `idx_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门户页面表';

-- 默认站点（门户内容首次部署即返回可用站点信息，页面由管理端维护）
INSERT INTO `portal_site` (`site_key`, `name`, `tagline`, `description`, `version`, `repository_url`, `footer_note`, `status`)
SELECT 'default', 'YunaNexus', '综合中控管理平台', '未来所有业务平台的统一底座 —— 统一身份、权限、文件与审计。', '0.1.0', '', 'Managed By YunaNexus Project', 1
WHERE NOT EXISTS (SELECT 1 FROM `portal_site` WHERE `site_key` = 'default');

SET FOREIGN_KEY_CHECKS = 1;

-- Durable service health observations (bounded to 31 days).
CREATE TABLE IF NOT EXISTS `notice_service_status_bucket` (
    `service_name` VARCHAR(32) NOT NULL,
    `bucket_start` BIGINT NOT NULL,
    `score_sum` DOUBLE NOT NULL,
    `sample_count` BIGINT NOT NULL,
    PRIMARY KEY (`service_name`, `bucket_start`)
) ENGINE=InnoDB;
