USE `yunanexus_auth`;
INSERT INTO `auth_client` (`client_code`, `client_name`, `redirect_uri`, `status`) VALUES ('web','YunaNexus 用户门户','http://103.39.66.135:3001/sso/callback',1) ON DUPLICATE KEY UPDATE `client_name`=VALUES(`client_name`), `redirect_uri`=VALUES(`redirect_uri`);
