USE yunanexus_auth;
CREATE TABLE IF NOT EXISTS auth_app_permission (
 client_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, permission_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 name VARCHAR(100) NOT NULL,PRIMARY KEY(client_code,permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS auth_app_role (
 client_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,role_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 name VARCHAR(100) NOT NULL,PRIMARY KEY(client_code,role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS auth_app_role_permission (
 client_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,role_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 permission_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,PRIMARY KEY(client_code,role_code,permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS auth_app_user_role (
 client_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,user_id BIGINT NOT NULL,
 role_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,PRIMARY KEY(client_code,user_id,role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
