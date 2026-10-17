package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.config.SecurityProperties;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real InnoDB transactions: CI always enables this suite against its disposable database. */
@EnabledIfSystemProperty(named="yunanexus.mysql.it",matches="true")
class PointsMysqlIntegrationTest {
 JdbcTemplate db;TransactionTemplate tx;PointsService service;
 final AuthenticatedIdentity web=new AuthenticatedIdentity("external","web","sid",91);
 final AuthenticatedIdentity app=new AuthenticatedIdentity("external","oauth_test","sid",91);
 static final String SECRET="test-only-client-credential";
 @BeforeEach void setup()throws Exception{
  var ds=new DriverManagerDataSource(System.getenv().getOrDefault("POINTS_TEST_MYSQL_URL","jdbc:mysql://127.0.0.1:3306/yunanexus_points_test?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"),"root",System.getenv("POINTS_TEST_MYSQL_PASSWORD"));
  db=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  db.execute("DROP TRIGGER IF EXISTS points_test_fail");
  db.execute("CREATE TABLE IF NOT EXISTS auth_client(client_code VARCHAR(64) PRIMARY KEY,application_category VARCHAR(32),status INT)");
  db.execute("CREATE TABLE IF NOT EXISTS auth_oauth_application(client_code VARCHAR(64) PRIMARY KEY,id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,owner_user_id BIGINT DEFAULT 1,secret_hash VARCHAR(64),scopes VARCHAR(256),status INT,deleted INT)");
  Path migration=Path.of("../misc/data/migrations/20261017-points.sql");
  for(String sql:Files.readString(migration).split(";"))if(!sql.isBlank()&&!sql.strip().startsWith("USE "))db.execute(sql);
  for(String table:List.of("auth_points_ledger","auth_points_account","auth_oauth_application","auth_client"))db.execute("DELETE FROM "+table);
  db.update("INSERT INTO auth_client VALUES('oauth_test','OFFICIAL_THIRD_PARTY',1)");
  db.update("INSERT INTO auth_oauth_application(client_code,secret_hash,scopes,status,deleted) VALUES('oauth_test',?,'points:read,points:write',1,0)",OAuthApplicationService.hash(SECRET));
  service=at("2026-10-17T15:59:00Z");
 }
 PointsService at(String instant){return new PointsService(db,Clock.fixed(Instant.parse(instant),ZoneOffset.UTC),new OAuthApplicationService(db,new SecurityProperties()));}
 Map<String,Object> check(PointsService s){return tx.execute(status->s.checkIn(web));}
 Map<String,Object> change(String key,long amount,boolean credit){return tx.execute(status->service.change(app,new PointsService.Change(key,amount,credit,"business",SECRET)));}
 long balance(){return db.queryForObject("SELECT balance FROM auth_points_account WHERE user_id=91",Long.class);}
 @Test void jdbcLedgerTimestampsHaveExplicitUtcOffset(){
  change("utc-contract",10,true);
  var rows=service.ledger(web,0,20);assertEquals(1,rows.size());
  assertEquals(Instant.parse("2026-10-17T15:59:00Z"),Instant.parse(rows.getFirst().get("createdAt").toString()));
 }
 @Test void repeatedAndConcurrentCheckInCreditsOnce()throws Exception{
  var pool=Executors.newFixedThreadPool(8);
  try{var calls=new ArrayList<Callable<Map<String,Object>>>();for(int i=0;i<20;i++)calls.add(()->check(service));for(var f:pool.invokeAll(calls))assertEquals(10L,((Number)f.get().get("balance")).longValue());}finally{pool.shutdownNow();}
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM auth_points_ledger",Integer.class));
  assertEquals(22L,((Number)check(at("2026-10-17T16:01:00Z")).get("balance")).longValue());
  assertEquals(32L,((Number)check(at("2026-10-19T16:01:00Z")).get("balance")).longValue());
 }
 @Test void concurrentDebitsCannotOverdraw()throws Exception{
  change("credit",1000,true);db.update("UPDATE auth_client SET application_category='THIRD_PARTY'");
  var pool=Executors.newFixedThreadPool(8);
  try{var calls=new ArrayList<Callable<Boolean>>();for(int i=0;i<20;i++){final String key="debit-"+i;calls.add(()->{try{change(key,100,false);return true;}catch(BizException e){return false;}});}int successes=0;for(var f:pool.invokeAll(calls))if(f.get())successes++;assertEquals(10,successes);}finally{pool.shutdownNow();}
  assertEquals(0,balance());assertEquals(11,db.queryForObject("SELECT COUNT(*) FROM auth_points_ledger",Integer.class));
 }
 @Test void retryIsIdempotentAndParameterConflictRejected(){
  var first=change("order",100,true);assertEquals(first,change("order",100,true));assertEquals(100,balance());
  assertThrows(BizException.class,()->change("order",101,true));assertEquals(100,balance());
 }
 @Test void unofficialCreditNegativeAmountAndBadSecretRejected(){
  change("initial",50,true);db.update("UPDATE auth_client SET application_category='THIRD_PARTY'");
  assertThrows(BizException.class,()->change("forbidden",20,true));assertThrows(BizException.class,()->change("negative",-20,false));
  assertThrows(BizException.class,()->tx.execute(status->service.change(app,new PointsService.Change("bad-secret",10,false,"business","wrong"))));assertEquals(50,balance());
 }
 @Test void databaseFailureRollsBackLedgerAndBalance(){
  tx.execute(status->service.account(web));
  db.execute("CREATE TRIGGER points_test_fail BEFORE UPDATE ON auth_points_account FOR EACH ROW SET NEW.balance=IF(NEW.balance>OLD.balance,-1,NEW.balance)");
  try{assertThrows(RuntimeException.class,()->change("rollback",100,true));assertEquals(0,balance());assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM auth_points_ledger",Integer.class));}finally{db.execute("DROP TRIGGER points_test_fail");}
 }
}
