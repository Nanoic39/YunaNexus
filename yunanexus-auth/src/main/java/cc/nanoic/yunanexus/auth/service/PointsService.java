package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.entity.ApplicationCategory;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.sql.Date;
import java.util.*;

/** Single account per user; a locked account and immutable ledger form one local transaction. */
@Service
public class PointsService {
 private final JdbcTemplate db;private final Clock clock;private final OAuthApplicationService applications;
 private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
 public PointsService(JdbcTemplate db,Clock clock,OAuthApplicationService applications){this.db=db;this.clock=clock;this.applications=applications;}
 private long user(AuthenticatedIdentity actor){if(actor==null||actor.userId()<=0)throw new BizException(ErrorCode.UNAUTHORIZED);return actor.userId();}
 private Map<String,Object> lock(long uid){
  db.update("INSERT INTO auth_points_account(user_id,balance,streak) VALUES(?,0,0) ON DUPLICATE KEY UPDATE user_id=auth_points_account.user_id",uid);
  return db.queryForMap("SELECT balance,streak,last_check_in FROM auth_points_account WHERE user_id=? FOR UPDATE",uid);
 }
 private LocalDate today(){return clock.instant().atZone(BUSINESS_ZONE).toLocalDate();}
 private LocalDate date(Object value){return value==null?null:value instanceof Date d?d.toLocalDate():LocalDate.parse(value.toString());}
 private Map<String,Object> view(Map<String,Object> row){
  var result=new LinkedHashMap<String,Object>();result.put("balance",row.get("balance"));result.put("streak",row.get("streak"));
  var previous=date(row.get("last_check_in"));result.put("lastCheckIn",previous);result.put("today",today());result.put("timeZone",BUSINESS_ZONE.getId());
  result.put("checkedInToday",today().equals(previous));result.put("nextReward",CheckInPolicy.reward(CheckInPolicy.nextStreak(previous,((Number)row.get("streak")).intValue(),today())));return result;
 }
 @Transactional public Map<String,Object> account(AuthenticatedIdentity actor){return view(lock(user(actor)));}
 @Transactional public Map<String,Object> checkIn(AuthenticatedIdentity actor){
  long uid=user(actor);if(!"web".equals(actor.clientCode()))throw new BizException(ErrorCode.FORBIDDEN,"请在门户签到");
  var account=lock(uid);LocalDate day=today(),previous=date(account.get("last_check_in"));
  if(day.equals(previous))return view(account);
  int streak=CheckInPolicy.nextStreak(previous,((Number)account.get("streak")).intValue(),day);
  long amount=CheckInPolicy.reward(streak),balance=Math.addExact(((Number)account.get("balance")).longValue(),amount);
  db.update("INSERT INTO auth_points_ledger(user_id,client_code,business_key,delta,balance,reason,created_at) VALUES(?,?,?,?,?,?,?)",uid,"__checkin",day.toString(),amount,balance,"每日签到",LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
  db.update("UPDATE auth_points_account SET balance=?,streak=?,last_check_in=? WHERE user_id=?",balance,streak,day,uid);
  account.put("balance",balance);account.put("streak",streak);account.put("last_check_in",day);return view(account);
 }
 public record Change(String businessKey,long amount,boolean credit,String reason,String clientSecret){}
 @Transactional public Map<String,Object> change(AuthenticatedIdentity actor,Change change){
  long uid=user(actor);String app=actor.clientCode();
  if(app==null||!app.startsWith("oauth_"))throw new BizException(ErrorCode.FORBIDDEN,"仅应用服务端可进行积分交易");
  if(change==null||change.businessKey()==null||!change.businessKey().matches("[A-Za-z0-9._:-]{1,96}")||change.amount()<1||change.amount()>1_000_000||change.reason()==null||change.reason().isBlank()||change.reason().length()>200)throw new BizException(ErrorCode.BAD_REQUEST,"积分交易参数无效");
  applications.verifySecret(app,change.clientSecret());
  if(!applications.allows(app,"POST","/api/v1/auth/account/points/transactions"))throw new BizException(ErrorCode.FORBIDDEN);
  var clients=db.queryForList("SELECT application_category FROM auth_client WHERE client_code=? AND status=1",app);
  if(clients.size()!=1||change.credit()&&!ApplicationCategory.allowsCredit(String.valueOf(clients.getFirst().get("application_category"))))throw new BizException(ErrorCode.FORBIDDEN,"非官方应用禁止增加积分");
  var account=lock(uid);long delta=change.credit()?change.amount():-change.amount();
  var existing=db.queryForList("SELECT id,delta,balance,reason FROM auth_points_ledger WHERE user_id=? AND client_code=? AND business_key=?",uid,app,change.businessKey());
  if(!existing.isEmpty()){
   var old=existing.getFirst();if(((Number)old.get("delta")).longValue()!=delta||!change.reason().equals(old.get("reason")))throw new BizException(ErrorCode.BAD_REQUEST,"业务幂等键与原交易不一致");return old;
  }
  long balance=Math.addExact(((Number)account.get("balance")).longValue(),delta);
  if(balance<0)throw new BizException(ErrorCode.BAD_REQUEST,"积分余额不足");
  db.update("INSERT INTO auth_points_ledger(user_id,client_code,business_key,delta,balance,reason,created_at) VALUES(?,?,?,?,?,?,?)",uid,app,change.businessKey(),delta,balance,change.reason(),LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
  db.update("UPDATE auth_points_account SET balance=? WHERE user_id=?",balance,uid);
  return db.queryForMap("SELECT id,delta,balance,reason FROM auth_points_ledger WHERE user_id=? AND client_code=? AND business_key=?",uid,app,change.businessKey());
 }
 public List<Map<String,Object>> ledger(AuthenticatedIdentity actor,long before,int size){
  long uid=user(actor);if(before<0||size<1||size>100)throw new BizException(ErrorCode.BAD_REQUEST);
  String scope="web".equals(actor.clientCode())?"":" AND client_code=?";
  List<Object> args=new ArrayList<>();args.add(uid);if(!scope.isEmpty())args.add(actor.clientCode());args.add(before==0?Long.MAX_VALUE:before);args.add(size);
  var rows=db.queryForList("SELECT id,client_code AS clientCode,business_key AS businessKey,delta,balance,reason,created_at AS createdAt FROM auth_points_ledger WHERE user_id=?"+scope+" AND id<? ORDER BY id DESC LIMIT ?",args.toArray());
  rows.forEach(row->row.replaceAll((k,v)->v instanceof java.sql.Timestamp t?t.toLocalDateTime().atOffset(ZoneOffset.UTC).toString():v));return rows;
 }
}
