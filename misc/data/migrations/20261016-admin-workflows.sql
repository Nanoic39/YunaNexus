USE yunanexus_auth;
CREATE TABLE IF NOT EXISTS auth_oauth_application (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_user_id BIGINT NOT NULL, client_code VARCHAR(64) NOT NULL UNIQUE,
 secret_hash CHAR(64) NOT NULL, app_name VARCHAR(64) NOT NULL, description VARCHAR(1000),logo VARCHAR(500), website_url VARCHAR(500),
 redirect_uris TEXT NOT NULL, scopes VARCHAR(255) NOT NULL,status TINYINT NOT NULL DEFAULT 0,audit_remark VARCHAR(500),audit_time DATETIME,
 deleted TINYINT NOT NULL DEFAULT 0,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 KEY idx_owner(owner_user_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS auth_password_reset_record (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,user_id BIGINT NOT NULL,username VARCHAR(64) NOT NULL,event VARCHAR(32) NOT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,KEY idx_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Idempotent column addition for existing production databases.
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='yunanexus_auth' AND table_name='auth_user' AND column_name='password_reset_required')=0,'ALTER TABLE auth_user ADD COLUMN password_reset_required TINYINT NOT NULL DEFAULT 0','SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
USE yunanexus_user_0;
CREATE TABLE IF NOT EXISTS badge_definition (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,badge_code VARCHAR(64) NOT NULL UNIQUE,badge_name VARCHAR(64) NOT NULL,badge_level INT NOT NULL DEFAULT 1,
 description VARCHAR(255),icon VARCHAR(512),grant_type VARCHAR(16) NOT NULL DEFAULT 'manual',condition_expr VARCHAR(500),status TINYINT NOT NULL DEFAULT 1,deleted TINYINT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS badge_grant_record (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,badge_id BIGINT NOT NULL,user_id BIGINT NOT NULL,username VARCHAR(64),completed TINYINT NOT NULL DEFAULT 0,granted_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_badge_user(badge_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

USE yunanexus_permission;
INSERT IGNORE INTO perm_role(role_code,role_name,description,data_scope,status) VALUES
 ('user','普通用户','注册默认角色，仅本人资料与个人工作空间',4,1),
 ('admin','管理员','日常业务管理，不具备超级管理员角色分配能力',1,1),
 ('super_admin','超级管理员','系统角色与权限维护；密码始终以不可逆哈希存储',1,1);
INSERT IGNORE INTO perm_permission(perm_code,resource,action,description,status) VALUES
 ('system:manage','/api/v1/**/admin/**','*','系统管理接口',1),
 ('account:reset-records:read','/api/v1/auth/admin/password-reset-records','read','查看密码重置审计记录，不含密码',1),
 ('account:profile:read','/api/v1/user/profile','read','读取本人资料',1),
 ('account:profile:update','/api/v1/user/profile','write','修改本人资料',1),
 ('account:password:change','/api/v1/auth/account/password','write','修改本人密码',1),
 ('account:files:read','/api/v1/file/me/**','read','读取本人文件',1),
 ('account:files:write','/api/v1/file/me/**','write','管理本人文件',1),
 ('account:applications:manage','/api/v1/auth/account/**','*','本人授权和接入应用',1),
 ('account:notices:read','/api/v1/notice/inbox/**','read','读取本人通知',1);
INSERT IGNORE INTO perm_role_permission(role_id,permission_id) SELECT r.id,p.id FROM perm_role r CROSS JOIN perm_permission p WHERE r.role_code IN ('admin','super_admin') AND p.perm_code IN ('system:manage','account:reset-records:read');
INSERT IGNORE INTO perm_role_permission(role_id,permission_id) SELECT r.id,p.id FROM perm_role r CROSS JOIN perm_permission p WHERE r.role_code IN ('user','super_admin') AND p.perm_code LIKE 'account:%';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) VALUES
 ('workspace','个人工作空间',0,'/console',10,1),('system','系统管理',0,NULL,50,1);
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'workspace:profile','个人设置',id,'/console/profile',10,1 FROM perm_menu WHERE menu_code='workspace';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'workspace:files','我的文件',id,'/console/files',20,1 FROM perm_menu WHERE menu_code='workspace';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'workspace:shares','我的分享',id,'/console/shares',30,1 FROM perm_menu WHERE menu_code='workspace';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'workspace:applications','已授权应用',id,'/console/applications',40,1 FROM perm_menu WHERE menu_code='workspace';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'workspace:api-keys','个人API Key',id,'/console/api-keys',50,1 FROM perm_menu WHERE menu_code='workspace';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'workspace:oauth-apps','应用接入',id,'/console/oauth-apps',60,1 FROM perm_menu WHERE menu_code='workspace';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'system:users','用户管理',id,'/users',10,1 FROM perm_menu WHERE menu_code='system';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'system:roles','角色管理',id,'/roles',20,1 FROM perm_menu WHERE menu_code='system';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'system:permissions','权限管理',id,'/permissions',30,1 FROM perm_menu WHERE menu_code='system';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'system:menus','菜单管理',id,'/menus',40,1 FROM perm_menu WHERE menu_code='system';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'system:badges','徽章管理',id,'/badges',50,1 FROM perm_menu WHERE menu_code='system';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'system:oauth-apps','应用管理',id,'/oauth-apps',60,1 FROM perm_menu WHERE menu_code='system';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'system:password-resets','密码重置记录',id,'/password-resets',70,1 FROM perm_menu WHERE menu_code='system';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'workspace:dashboard','个人概览',id,'/console/dashboard',0,1 FROM perm_menu WHERE menu_code='workspace';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'workspace:settings','账号设置',id,'/console/settings',15,1 FROM perm_menu WHERE menu_code='workspace';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'workspace:about','项目信息',id,'/console/about',80,1 FROM perm_menu WHERE menu_code='workspace';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'system:overview','管理概览',id,'/overview',0,1 FROM perm_menu WHERE menu_code='system';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'system:analytics','数据分析',id,'/analytics',5,1 FROM perm_menu WHERE menu_code='system';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'system:depts','部门管理',id,'/depts',35,1 FROM perm_menu WHERE menu_code='system';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'system:appeals','申诉审核',id,'/appeals',45,1 FROM perm_menu WHERE menu_code='system';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'system:content','门户内容',id,'/content',80,1 FROM perm_menu WHERE menu_code='system';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'system:notice-templates','通知模板',id,'/notice/templates',90,1 FROM perm_menu WHERE menu_code='system';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'system:notice-send','发送通知',id,'/notice/send',100,1 FROM perm_menu WHERE menu_code='system';
INSERT IGNORE INTO perm_menu(menu_code,menu_name,parent_id,path,sort,status) SELECT 'system:notice-records','发送记录',id,'/notice/records',110,1 FROM perm_menu WHERE menu_code='system';
INSERT IGNORE INTO perm_role_menu(role_id,menu_id) SELECT r.id,m.id FROM perm_role r CROSS JOIN perm_menu m WHERE (r.role_code IN ('admin','super_admin') AND (m.menu_code='system' OR m.menu_code LIKE 'system:%')) OR (r.role_code IN ('user','super_admin') AND (m.menu_code='workspace' OR m.menu_code LIKE 'workspace:%'));
INSERT IGNORE INTO perm_user_role(user_id,role_id) SELECT u.user_id,r.id FROM yunanexus_auth.auth_user u CROSS JOIN perm_role r WHERE r.role_code='user';

UPDATE perm_permission SET action=CASE action WHEN 'GET' THEN 'read' WHEN 'POST' THEN 'write' ELSE action END WHERE perm_code IN ('account:reset-records:read','account:profile:read','account:profile:update','account:password:change','account:files:read','account:files:write','account:notices:read') AND action IN ('GET','POST');
