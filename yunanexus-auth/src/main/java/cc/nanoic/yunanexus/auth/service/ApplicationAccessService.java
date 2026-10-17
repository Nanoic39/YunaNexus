package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Application RBAC has separate tables and never writes the platform role/permission catalog. */
@Service
public class ApplicationAccessService {
 private final JdbcTemplate db;private final OAuthApplicationService applications;
 public ApplicationAccessService(JdbcTemplate db,OAuthApplicationService applications){this.db=db;this.applications=applications;}
 private String owner(AuthenticatedIdentity actor,long id){
  if(actor==null||!"web".equals(actor.clientCode()))throw forbidden();
  var rows=db.queryForList("SELECT client_code FROM auth_oauth_application WHERE id=? AND owner_user_id=? AND deleted=0 AND status=1 FOR UPDATE",id,actor.userId());
  if(rows.size()!=1)throw forbidden();return rows.getFirst().get("client_code").toString();
 }
 private static BizException forbidden(){return new BizException(ErrorCode.FORBIDDEN);}
 private static void code(String code){if(code==null||!code.matches("[a-zA-Z][a-zA-Z0-9_.:-]{0,63}"))throw new BizException(ErrorCode.BAD_REQUEST,"权限或角色代码无效");}
 private static void name(String name){if(name==null||name.isBlank()||name.length()>100)throw new BizException(ErrorCode.BAD_REQUEST,"名称无效");}
 private void capacity(String app,String table){
  // Table is a server constant, never taken from a request.
  if(db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE client_code=?",Integer.class,app)>=200)throw new BizException(ErrorCode.BAD_REQUEST,"每应用最多200项");
 }
 @Transactional public Map<String,Object> catalog(AuthenticatedIdentity actor,long id){
  String app=owner(actor,id);return Map.of("clientId",app,"permissions",db.queryForList("SELECT permission_code AS code,name FROM auth_app_permission WHERE client_code=? ORDER BY permission_code",app),"roles",db.queryForList("SELECT role_code AS code,name FROM auth_app_role WHERE client_code=? ORDER BY role_code",app),"bindings",db.queryForList("SELECT role_code AS roleCode,permission_code AS permissionCode FROM auth_app_role_permission WHERE client_code=? ORDER BY role_code,permission_code",app));
 }
 public record Permission(String code,String name){}
 @Transactional public void permission(AuthenticatedIdentity actor,long id,Permission body){
  String app=owner(actor,id);code(body.code());name(body.name());
  if(db.queryForObject("SELECT COUNT(*) FROM auth_app_permission WHERE client_code=? AND permission_code=?",Integer.class,app,body.code())==0)capacity(app,"auth_app_permission");
  db.update("INSERT INTO auth_app_permission(client_code,permission_code,name) VALUES(?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name)",app,body.code(),body.name());
 }
 public record Role(String code,String name,List<String> permissions){}
 @Transactional public void role(AuthenticatedIdentity actor,long id,Role body){
  String app=owner(actor,id);code(body.code());name(body.name());
  if(body.permissions()==null||body.permissions().size()>200)throw new BizException(ErrorCode.BAD_REQUEST);
  Set<String> permissions=new LinkedHashSet<>(body.permissions());
  for(String permission:permissions){code(permission);if(db.queryForObject("SELECT COUNT(*) FROM auth_app_permission WHERE client_code=? AND permission_code=?",Integer.class,app,permission)!=1)throw new BizException(ErrorCode.BAD_REQUEST,"角色只能绑定本应用已有权限");}
  if(db.queryForObject("SELECT COUNT(*) FROM auth_app_role WHERE client_code=? AND role_code=?",Integer.class,app,body.code())==0)capacity(app,"auth_app_role");
  db.update("INSERT INTO auth_app_role(client_code,role_code,name) VALUES(?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name)",app,body.code(),body.name());
  db.update("DELETE FROM auth_app_role_permission WHERE client_code=? AND role_code=?",app,body.code());
  for(String permission:permissions)db.update("INSERT INTO auth_app_role_permission(client_code,role_code,permission_code) VALUES(?,?,?)",app,body.code(),permission);
 }
 @Transactional public void delete(AuthenticatedIdentity actor,long id,String code,boolean role){
  String app=owner(actor,id);code(code);
  if(role){db.update("DELETE FROM auth_app_user_role WHERE client_code=? AND role_code=?",app,code);db.update("DELETE FROM auth_app_role_permission WHERE client_code=? AND role_code=?",app,code);db.update("DELETE FROM auth_app_role WHERE client_code=? AND role_code=?",app,code);}
  else {db.update("DELETE FROM auth_app_role_permission WHERE client_code=? AND permission_code=?",app,code);db.update("DELETE FROM auth_app_permission WHERE client_code=? AND permission_code=?",app,code);}
 }
 @Transactional public Map<String,Object> memberRoles(AuthenticatedIdentity actor,long id,String externalId){
  String app=owner(actor,id);if(externalId==null||externalId.length()>64)throw new BizException(ErrorCode.BAD_REQUEST);
  var users=db.queryForList("SELECT u.user_id FROM auth_user u JOIN auth_platform p ON p.user_id=u.user_id AND p.client_code=? AND p.status=1 WHERE u.external_id=? AND u.status=1",app,externalId);
  if(users.size()!=1)throw new BizException(ErrorCode.NOT_FOUND,"用户未授权本应用");
  long uid=((Number)users.getFirst().get("user_id")).longValue();
  return Map.of("externalId",externalId,"roles",db.queryForList("SELECT role_code FROM auth_app_user_role WHERE client_code=? AND user_id=? ORDER BY role_code",String.class,app,uid));
 }
 public record Member(String externalId,List<String> roles){}
 @Transactional public void member(AuthenticatedIdentity actor,long id,Member body){
  String app=owner(actor,id);
  if(body.externalId()==null||body.externalId().length()>64||body.roles()==null||body.roles().size()>200)throw new BizException(ErrorCode.BAD_REQUEST);
  var users=db.queryForList("SELECT u.user_id FROM auth_user u JOIN auth_platform p ON p.user_id=u.user_id AND p.client_code=? AND p.status=1 WHERE u.external_id=? AND u.status=1",app,body.externalId());
  if(users.size()!=1)throw new BizException(ErrorCode.NOT_FOUND,"用户未授权本应用");long uid=((Number)users.getFirst().get("user_id")).longValue();
  var roles=new LinkedHashSet<>(body.roles());
  for(String role:roles){code(role);if(db.queryForObject("SELECT COUNT(*) FROM auth_app_role WHERE client_code=? AND role_code=?",Integer.class,app,role)!=1)throw new BizException(ErrorCode.BAD_REQUEST,"角色不存在于本应用");}
  db.update("DELETE FROM auth_app_user_role WHERE client_code=? AND user_id=?",app,uid);
  for(String role:roles)db.update("INSERT INTO auth_app_user_role(client_code,user_id,role_code) VALUES(?,?,?)",app,uid,role);
 }
 public record Check(List<String> permissions,String clientSecret){}
 @Transactional(readOnly=true) public Map<String,Boolean> check(AuthenticatedIdentity actor,Check body){
  if(actor==null||actor.clientCode()==null||!actor.clientCode().startsWith("oauth_"))throw forbidden();
  if(body==null||body.permissions()==null||body.permissions().size()>100)throw new BizException(ErrorCode.BAD_REQUEST);
  String app=actor.clientCode();applications.verifySecret(app,body.clientSecret());
  if(!applications.allows(app,"POST","/api/v1/auth/application/permissions/check"))throw forbidden();
  var grant=db.queryForObject("SELECT COUNT(*) FROM auth_platform WHERE user_id=? AND client_code=? AND status=1",Integer.class,actor.userId(),app);
  if(grant==null||grant!=1)throw forbidden();
  var allowed=new HashSet<>(db.queryForList("SELECT DISTINCT rp.permission_code FROM auth_app_user_role ur JOIN auth_app_role r ON r.client_code=ur.client_code AND r.role_code=ur.role_code JOIN auth_app_role_permission rp ON rp.client_code=r.client_code AND rp.role_code=r.role_code JOIN auth_app_permission p ON p.client_code=rp.client_code AND p.permission_code=rp.permission_code WHERE ur.client_code=? AND ur.user_id=?",String.class,app,actor.userId()));
  var result=new LinkedHashMap<String,Boolean>();for(String permission:body.permissions()){code(permission);result.put(permission,allowed.contains(permission));}return result;
 }
}
