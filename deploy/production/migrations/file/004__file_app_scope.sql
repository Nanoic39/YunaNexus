-- 多应用文件隔离 + 归属类型 + 可见性
--
-- 目的：三方应用只能访问"该用户在本应用下"的文件；官方前端可管理全部；
--       系统内部公开文件（头像等）对所有登录用户可见。
--
-- 回填策略：存量数据全部视为"官方前端上传的用户私有文件"（app_id 为空），向后兼容。
ALTER TABLE file_meta
    ADD COLUMN owner_type VARCHAR(16) NOT NULL DEFAULT 'user' COMMENT 'user/system/app',
    ADD COLUMN app_id     VARCHAR(64) NULL COMMENT '所属应用；空表示官方前端',
    ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'private' COMMENT 'private/internal';

CREATE INDEX idx_file_meta_app ON file_meta (create_by, app_id, status);
CREATE INDEX idx_file_meta_visibility ON file_meta (visibility, status);
