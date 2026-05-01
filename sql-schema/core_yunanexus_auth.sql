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

--
SET FOREIGN_KEY_CHECKS = 1;