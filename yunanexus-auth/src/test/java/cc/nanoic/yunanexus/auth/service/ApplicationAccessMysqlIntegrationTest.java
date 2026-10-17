package cc.nanoic.yunanexus.auth.service;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.config.SecurityProperties;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="yunanexus.mysql.it",matches="true")
class ApplicationAccessMysqlIntegrationTest {
 PointsMysqlIntegrationTest fixture=new PointsMysqlIntegrationTest();ApplicationAccessService access;long a,b;
 final AuthenticatedIdentity owner=new AuthenticatedIdentity("owner","web","owner-sid",1);
 final AuthenticatedIdentity userA=new AuthenticatedIdentity("member","oauth_test","member-sid",91);
 final AuthenticatedIdentity userB=new AuthenticatedIdentity("member","oauth_second","member-sid",91);
 @BeforeEach void setup()throws Exception{
  fixture.setup();var db=fixture.db;
  for(String sql:Files.readString(Path.of("../misc/data/migrations/20261017-application-access.sql")).split(";"))if(!sql.isBlank()&&!sql.strip().startsWith("USE "))db.execute(sql);
  for(String table:List.of("auth_app_user_role","auth_app_role_permission","auth_app_role","auth_app_permission"))db.execute("DELETE FROM "+table);
  db.execute("CREATE TABLE IF NOT EXISTS auth_user(user_id BIGINT PRIMARY KEY,external_id VARCHAR(64),status INT)");
  db.execute("CREATE TABLE IF NOT EXISTS auth_platform(user_id BIGINT,client_code VARCHAR(64),status INT,PRIMARY KEY(user_id,client_code))");
  db.execute("DELETE FROM auth_user");db.execute("DELETE FROM auth_platform");
  db.update("INSERT INTO auth_user VALUES(91,'member',1)");
  db.update("UPDATE auth_oauth_application SET scopes='permissions:read',owner_user_id=1");
  db.update("INSERT INTO auth_oauth_application(client_code,owner_user_id,secret_hash,scopes,status,deleted) VALUES('oauth_second',2,?,'permissions:read',1,0)",OAuthApplicationService.hash(PointsMysqlIntegrationTest.SECRET));
  db.update("INSERT INTO auth_platform VALUES(91,'oauth_test',1),(91,'oauth_second',1)");
  a=db.queryForObject("SELECT id FROM auth_oauth_application WHERE client_code='oauth_test'",Long.class);b=db.queryForObject("SELECT id FROM auth_oauth_application WHERE client_code='oauth_second'",Long.class);
  access=new ApplicationAccessService(db,new OAuthApplicationService(db,new SecurityProperties()));
 }
 @Test void ownerCannotReadOrModifyAnotherApplication(){
  assertThrows(BizException.class,()->fixture.tx.execute(s->access.catalog(owner,b)));
  assertThrows(BizException.class,()->fixture.tx.execute(s->{access.permission(owner,b,new ApplicationAccessService.Permission("posts:read","read"));return null;}));
  assertEquals(0,fixture.db.queryForObject("SELECT COUNT(*) FROM auth_app_permission",Integer.class));
 }
 @Test void rolesAndPermissionsAreIsolatedAndRevocationImmediate(){
  fixture.tx.execute(s->{access.permission(owner,a,new ApplicationAccessService.Permission("posts:read","read"));access.role(owner,a,new ApplicationAccessService.Role("reader","reader",List.of("posts:read")));access.member(owner,a,new ApplicationAccessService.Member("member",List.of("reader")));return null;});
  var check=new ApplicationAccessService.Check(List.of("posts:read","system:admin"),PointsMysqlIntegrationTest.SECRET);
  assertEquals(List.of("reader"),fixture.tx.execute(s->access.memberRoles(owner,a,"member")).get("roles"));
  assertEquals(Map.of("posts:read",true,"system:admin",false),access.check(userA,check));
  assertFalse(access.check(userB,check).get("posts:read"));
  fixture.tx.execute(s->{access.delete(owner,a,"reader",true);return null;});assertFalse(access.check(userA,check).get("posts:read"));
 }
 @Test void cannotBindPermissionOnlyDefinedInOtherApp(){
  fixture.db.update("INSERT INTO auth_app_permission VALUES('oauth_second','foreign:write','foreign')");
  assertThrows(BizException.class,()->fixture.tx.execute(s->{access.role(owner,a,new ApplicationAccessService.Role("editor","editor",List.of("foreign:write")));return null;}));
  assertEquals(0,fixture.db.queryForObject("SELECT COUNT(*) FROM auth_app_role",Integer.class));
 }
 @Test void revokedUserGrantAndWrongCredentialFailClosed(){
  fixture.db.update("UPDATE auth_platform SET status=0 WHERE client_code='oauth_test'");
  assertThrows(BizException.class,()->access.check(userA,new ApplicationAccessService.Check(List.of("posts:read"),PointsMysqlIntegrationTest.SECRET)));
  assertThrows(BizException.class,()->access.check(userB,new ApplicationAccessService.Check(List.of("posts:read"),"wrong")));
  assertThrows(BizException.class,()->fixture.tx.execute(s->{access.member(owner,a,new ApplicationAccessService.Member("member",List.of()));return null;}));
 }
}
